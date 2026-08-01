package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LongLivedProotAdmissionSimulatorTest {
    @Test
    fun `duplicate owner request returns existing lease without consuming capacity`() {
        val simulator = simulator(globalMax = 2)
        val owner = owner("service-one")
        val first = simulator.request(spec(owner), 1L)
        val duplicate = simulator.request(spec(owner), 2L)

        assertEquals(LongLivedProotRequestDisposition.CREATED, first.disposition)
        assertEquals(LongLivedProotRequestDisposition.EXISTING, duplicate.disposition)
        assertSame(simulator.record(owner), duplicate.record)
        assertEquals(1, simulator.snapshot().activeCount)
        assertEquals(0, simulator.snapshot().queuedCount)
    }

    @Test
    fun `duplicate owner cannot silently replace its admitted specification`() {
        val simulator = simulator(globalMax = 2)
        val owner = owner("service-one")
        val original = simulator.request(spec(owner), 1L)
        val conflict = simulator.request(
            spec(owner, posture = LongLivedProotFilesystemPosture.ISOLATED_RUNTIME),
            2L,
        )

        assertEquals(LongLivedProotRequestDisposition.SPEC_CONFLICT, conflict.disposition)
        assertSame(original.record, conflict.record)
        assertEquals(LongLivedProotFilesystemPosture.SHARED_RUNTIME, simulator.record(owner)?.spec?.filesystemPosture)
        assertEquals(1, simulator.snapshot().activeCount)
    }

    @Test
    fun `global capacity admits only up to the configured limit`() {
        val simulator = simulator(globalMax = 2)
        val first = owner("first")
        val second = owner("second")
        val third = owner("third")

        simulator.request(spec(first), 1L)
        simulator.request(spec(second), 2L)
        simulator.request(spec(third), 3L)

        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(first)?.phase)
        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(second)?.phase)
        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(third)?.phase)
        assertEquals(2, simulator.snapshot().activeCount)
        assertEquals(1, simulator.snapshot().queuedCount)
    }

    @Test
    fun `full lane does not block a runnable different lane`() {
        val lanes = RuntimeWorkloadPolicy.defaultLanes().map { lane ->
            when (lane.lane) {
                RuntimeLaneKind.INTERACTIVE -> lane.copy(maxConcurrency = 1)
                RuntimeLaneKind.SERVICE -> lane.copy(maxConcurrency = 1)
                else -> lane
            }
        }
        val simulator = LongLivedProotAdmissionSimulator(
            LongLivedProotAdmissionPolicy(globalMax = 2, lanes = lanes, pressure = RuntimePressureLevel.NORMAL)
        )
        val interactiveOne = owner("interactive-one", LongLivedProotOwnerKind.TERMINAL_SESSION)
        val interactiveTwo = owner("interactive-two", LongLivedProotOwnerKind.TERMINAL_SESSION)
        val service = owner("service")

        simulator.request(spec(interactiveOne, RuntimeLaneKind.INTERACTIVE), 1L)
        simulator.request(spec(interactiveTwo, RuntimeLaneKind.INTERACTIVE), 2L)
        simulator.request(spec(service, RuntimeLaneKind.SERVICE), 3L)

        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(interactiveTwo)?.phase)
        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(service)?.phase)
        assertEquals(2, simulator.snapshot().activeCount)
    }

    @Test
    fun `exclusive maintenance becomes a barrier and cannot starve`() {
        val simulator = simulator(globalMax = 2)
        val existing = owner("existing")
        val maintenance = owner("maintenance", LongLivedProotOwnerKind.OTHER_MANAGED_OWNER)
        val laterInteractive = owner("later", LongLivedProotOwnerKind.TERMINAL_SESSION)

        simulator.request(spec(existing), 1L)
        simulator.request(
            spec(
                maintenance,
                lane = RuntimeLaneKind.BUILD,
                posture = LongLivedProotFilesystemPosture.EXCLUSIVE_MAINTENANCE,
            ),
            2L,
        )
        simulator.request(spec(laterInteractive, RuntimeLaneKind.INTERACTIVE), 3L)

        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(maintenance)?.phase)
        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(laterInteractive)?.phase)
        assertTrue(simulator.snapshot().queuedExclusiveMaintenance)

        accepted(simulator.cancelBeforeStart(existing, 4L))
        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(maintenance)?.phase)
        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(laterInteractive)?.phase)
        assertTrue(simulator.snapshot().activeExclusiveMaintenance)

        accepted(simulator.cancelBeforeStart(maintenance, 5L))
        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(laterInteractive)?.phase)
        assertFalse(simulator.snapshot().activeExclusiveMaintenance)
    }

    @Test
    fun `pressure change never revokes existing owner and only essential work can enter`() {
        val simulator = simulator(globalMax = 2)
        val existing = owner("existing")
        val blocked = owner("blocked")
        val essential = owner("essential")

        simulator.request(spec(existing), 1L)
        simulator.updatePolicy(policy(globalMax = 2, pressure = RuntimePressureLevel.HIGH), 2L)
        simulator.request(spec(blocked, RuntimeLaneKind.INTERACTIVE), 3L)
        simulator.request(spec(essential, pressureEssential = true), 4L)

        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(existing)?.phase)
        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(blocked)?.phase)
        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(essential)?.phase)

        accepted(simulator.cancelBeforeStart(existing, 5L))
        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(blocked)?.phase)
        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(essential)?.phase)
        assertEquals(RuntimePressureLevel.HIGH, simulator.snapshot().pressure)
    }

    @Test
    fun `pressure blocked maintenance does not become a barrier for essential work`() {
        val simulator = LongLivedProotAdmissionSimulator(
            policy(globalMax = 2, pressure = RuntimePressureLevel.HIGH)
        )
        val maintenance = owner("maintenance", LongLivedProotOwnerKind.OTHER_MANAGED_OWNER)
        val essential = owner("essential")

        simulator.request(
            spec(
                maintenance,
                lane = RuntimeLaneKind.BUILD,
                posture = LongLivedProotFilesystemPosture.EXCLUSIVE_MAINTENANCE,
            ),
            1L,
        )
        simulator.request(spec(essential, pressureEssential = true), 2L)

        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(maintenance)?.phase)
        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(essential)?.phase)
        assertFalse(simulator.snapshot().activeExclusiveMaintenance)
    }

    @Test
    fun `same priority owners remain fifo when capacity is released`() {
        val simulator = simulator(globalMax = 1)
        val first = owner("first")
        val second = owner("second")
        val third = owner("third")

        simulator.request(spec(first), 1L)
        simulator.request(spec(second), 2L)
        simulator.request(spec(third), 3L)
        accepted(simulator.cancelBeforeStart(first, 4L))

        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(second)?.phase)
        assertEquals(LongLivedProotLeasePhase.REQUESTED, simulator.record(third)?.phase)
        accepted(simulator.cancelBeforeStart(second, 5L))
        assertEquals(LongLivedProotLeasePhase.ADMITTED, simulator.record(third)?.phase)
    }

    @Test
    fun `released owner receives a new generation on a later request`() {
        val simulator = simulator(globalMax = 1)
        val owner = owner("repeat")
        val first = simulator.request(spec(owner), 1L).record
        accepted(simulator.cancelBeforeStart(owner, 2L))
        val second = simulator.request(spec(owner), 3L).record

        assertEquals(first.generation + 1L, second.generation)
        assertTrue(first.leaseId != second.leaseId)
        assertEquals(LongLivedProotLeasePhase.ADMITTED, second.phase)
    }

    @Test
    fun `snapshot exposes only bounded planning aggregates`() {
        val simulator = simulator(globalMax = 2)
        simulator.request(spec(owner("one")), 1L)
        simulator.request(spec(owner("two")), 2L)
        val snapshot = simulator.snapshot()
        val fields = LongLivedProotAdmissionSnapshot::class.java.declaredFields.map { it.name }.toSet()

        assertEquals("planned_not_production", snapshot.scope)
        assertEquals(2, snapshot.activeByLane[RuntimeLaneKind.SERVICE])
        assertFalse(fields.any { it.contains("owner", ignoreCase = true) })
        assertFalse(fields.any { it.contains("pid", ignoreCase = true) })
        assertFalse(fields.any { it.contains("command", ignoreCase = true) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `simulator rejects globally stale events`() {
        val simulator = simulator(globalMax = 1)
        simulator.request(spec(owner("first")), 10L)
        simulator.updatePolicy(policy(1, RuntimePressureLevel.NORMAL), 9L)
    }

    private fun simulator(globalMax: Int): LongLivedProotAdmissionSimulator =
        LongLivedProotAdmissionSimulator(policy(globalMax, RuntimePressureLevel.NORMAL))

    private fun policy(
        globalMax: Int,
        pressure: RuntimePressureLevel,
    ) = LongLivedProotAdmissionPolicy(
        globalMax = globalMax,
        pressure = pressure,
    )

    private fun owner(
        id: String,
        kind: LongLivedProotOwnerKind = LongLivedProotOwnerKind.BACKGROUND_RUNTIME,
    ) = LongLivedProotOwnerKey(kind, id)

    private fun spec(
        owner: LongLivedProotOwnerKey,
        lane: RuntimeLaneKind = RuntimeLaneKind.SERVICE,
        posture: LongLivedProotFilesystemPosture = LongLivedProotFilesystemPosture.SHARED_RUNTIME,
        pressureEssential: Boolean = false,
    ) = LongLivedProotLeaseSpec(
        owner = owner,
        lane = lane,
        filesystemPosture = posture,
        pressureEssential = pressureEssential,
    )

    private fun accepted(result: LongLivedProotLeaseTransition) {
        assertTrue(result.rejectionReason, result.accepted)
    }
}
