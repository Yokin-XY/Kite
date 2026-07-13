package com.kite.app.application.runs

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeOwnerProbeCoordinatorTest {
    @Test
    fun `blank values use the stable probe identity`() {
        val calls = mutableListOf<String>()
        val coordinator = RuntimeOwnerProbeCoordinator { resourceId, instanceId ->
            calls += "$resourceId:$instanceId"
            RunCommandResult.Accepted(instanceId)
        }

        coordinator.start(RuntimeOwnerProbeRequest(" ", null))

        assertEquals(
            listOf("kite.owner.telemetry.probe:resource-owner-probe-kite.owner.telemetry.probe"),
            calls
        )
    }

    @Test
    fun `explicit resource and instance are normalized once`() {
        val calls = mutableListOf<String>()
        val coordinator = RuntimeOwnerProbeCoordinator { resourceId, instanceId ->
            calls += "$resourceId:$instanceId"
            RunCommandResult.Accepted(instanceId)
        }

        coordinator.start(RuntimeOwnerProbeRequest(" Demo Resource ", " probe-1 "))

        assertEquals(listOf("demo-resource:probe-1"), calls)
    }
}
