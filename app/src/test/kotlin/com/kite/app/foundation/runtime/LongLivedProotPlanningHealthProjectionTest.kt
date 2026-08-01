package com.kite.app.foundation.runtime

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongLivedProotPlanningHealthProjectionTest {
    @Test
    fun `planning projection uses a separate stable prefix and fixed enum counts`() {
        val simulator = LongLivedProotAdmissionSimulator(
            LongLivedProotAdmissionPolicy(globalMax = 2, pressure = RuntimePressureLevel.NORMAL)
        )
        simulator.request(spec(owner("service-one")), 1L)
        simulator.request(spec(owner("service-two")), 2L)
        val recovery = LongLivedProotRecoveryPlanner.plan(
            persisted = simulator.allRecords(),
            observedAlive = emptySet(),
            nowMs = 3L,
        )

        val fields = LongLivedProotPlanningHealthProjection.project(simulator.snapshot(), recovery)

        assertEquals("planned_not_production", fields["proot_long_planned_scope"])
        assertEquals("2", fields["proot_long_planned_active_count"])
        assertEquals("2", fields["proot_long_planned_recovery_decision_count"])
        assertEquals("2", fields["proot_long_planned_recovery_phase_released_count"])
        RuntimeLaneKind.entries.forEach { lane ->
            assertTrue(fields.containsKey("proot_long_planned_active_lane_${lane.name.lowercase(Locale.ROOT)}_count"))
            assertTrue(fields.containsKey("proot_long_planned_recovery_lane_${lane.name.lowercase(Locale.ROOT)}_count"))
        }
        LongLivedProotRecoveryAction.entries.forEach { action ->
            assertTrue(
                fields.containsKey(
                    "proot_long_planned_recovery_action_${action.name.lowercase(Locale.ROOT)}_count"
                )
            )
        }
        assertFalse(fields.keys.any { it.startsWith("proot_actual_") })
    }

    @Test
    fun `projection excludes owner process command path and session identities`() {
        val sensitiveOwner = "private-owner-527"
        val sensitiveLease = "private-lease-913"
        val running = running(owner(sensitiveOwner), sensitiveLease)
        val recovery = LongLivedProotRecoveryPlanner.plan(
            persisted = listOf(running),
            observedAlive = setOf(requireNotNull(running.processIdentity)),
            nowMs = 10L,
        )
        val admission = emptyAdmissionSnapshot()

        val fields = LongLivedProotPlanningHealthProjection.project(admission, recovery)
        val serialized = fields.entries.joinToString("\n") { "${it.key}=${it.value}" }

        listOf(
            sensitiveOwner,
            sensitiveLease,
            running.processIdentity!!.hostPid.toString(),
            running.processIdentity.processStartTicks.toString(),
            "/workspace/private",
            "openclaw acp",
            "agent-session-secret",
        ).forEach { forbidden ->
            assertFalse("projection leaked $forbidden", serialized.contains(forbidden))
        }
        assertTrue(fields.values.all { value ->
            value == "true" || value == "false" || value == "planned_not_production" ||
                value == "normal" || value.toLongOrNull() != null
        })
    }

    @Test
    fun `projection reads immutable snapshots without mutating simulator state`() {
        val simulator = LongLivedProotAdmissionSimulator(
            LongLivedProotAdmissionPolicy(globalMax = 1, pressure = RuntimePressureLevel.NORMAL)
        )
        simulator.request(spec(owner("first")), 1L)
        simulator.request(spec(owner("second")), 2L)
        val beforeSnapshot = simulator.snapshot()
        val beforeRecords = simulator.allRecords()

        val first = LongLivedProotPlanningHealthProjection.project(beforeSnapshot)
        val second = LongLivedProotPlanningHealthProjection.project(beforeSnapshot)

        assertEquals(first, second)
        assertEquals(beforeSnapshot, simulator.snapshot())
        assertEquals(beforeRecords, simulator.allRecords())
        assertEquals("false", first["proot_long_planned_recovery_available"])
    }

    @Test
    fun `schema cardinality does not grow with owner count`() {
        val one = LongLivedProotAdmissionSimulator(
            LongLivedProotAdmissionPolicy(globalMax = 8, pressure = RuntimePressureLevel.NORMAL)
        )
        one.request(spec(owner("one")), 1L)
        val many = LongLivedProotAdmissionSimulator(
            LongLivedProotAdmissionPolicy(globalMax = 8, pressure = RuntimePressureLevel.NORMAL)
        )
        repeat(8) { index -> many.request(spec(owner("many-$index")), index.toLong() + 1L) }

        val oneFields = LongLivedProotPlanningHealthProjection.project(one.snapshot())
        val manyFields = LongLivedProotPlanningHealthProjection.project(many.snapshot())

        assertEquals(oneFields.keys, manyFields.keys)
        assertEquals(oneFields.size, manyFields.size)
    }

    private fun emptyAdmissionSnapshot() = LongLivedProotAdmissionSnapshot(
        pressure = RuntimePressureLevel.NORMAL,
        configuredGlobalMax = 2,
        effectiveGlobalMax = 2,
        activeCount = 0,
        queuedCount = 0,
        activeExclusiveMaintenance = false,
        queuedExclusiveMaintenance = false,
        activeByLane = RuntimeLaneKind.entries.associateWith { 0 },
        queuedByLane = RuntimeLaneKind.entries.associateWith { 0 },
    )

    private fun running(
        owner: LongLivedProotOwnerKey,
        leaseId: String,
    ): LongLivedProotLeaseRecord {
        val requested = LongLivedProotOwnerLeaseTransitions.requested(
            leaseId = leaseId,
            generation = 1L,
            spec = spec(owner),
            nowMs = 1L,
        )
        val admitted = accepted(LongLivedProotOwnerLeaseTransitions.admit(requested, 2L))
        val starting = accepted(LongLivedProotOwnerLeaseTransitions.beginStart(admitted, 3L))
        return accepted(
            LongLivedProotOwnerLeaseTransitions.attachProcess(
                starting,
                LongLivedProotProcessIdentity(49157, 835921L),
                4L,
            )
        )
    }

    private fun owner(id: String) =
        LongLivedProotOwnerKey(LongLivedProotOwnerKind.BACKGROUND_RUNTIME, id)

    private fun spec(owner: LongLivedProotOwnerKey) = LongLivedProotLeaseSpec(
        owner = owner,
        lane = RuntimeLaneKind.SERVICE,
        filesystemPosture = LongLivedProotFilesystemPosture.SHARED_RUNTIME,
    )

    private fun accepted(transition: LongLivedProotLeaseTransition): LongLivedProotLeaseRecord {
        check(transition.accepted) { transition.rejectionReason ?: "unexpected_projection_test_rejection" }
        return transition.record
    }
}
