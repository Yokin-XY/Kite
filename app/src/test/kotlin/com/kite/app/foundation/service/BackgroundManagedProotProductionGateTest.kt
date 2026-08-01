package com.kite.app.foundation.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundManagedProotProductionGateTest {
    @Test
    fun `production gate is explicitly enabled only by the rf950 acceptance`() {
        val snapshot = BackgroundManagedProotProductionGate.snapshot()

        assertEquals("background_managed_proot_gate_v1", snapshot.schema)
        assertEquals(BackgroundManagedProotGateState.ENABLED, snapshot.state)
        assertEquals("rf950_matrix_passed", snapshot.reason)
        BackgroundManagedProotProductionGate.requireEnabled()
    }

    @Test
    fun `host checks the category gate before admission and process creation`() {
        val source = File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt"
        ).readText().substringAfter("private fun startProcessRuntime(")
            .substringBefore("private fun buildRuntimeProcessLaunchConfig(")
        val gateAt = source.indexOf("BackgroundManagedProotProductionGate.requireEnabled()")
        val admissionAt = source.indexOf("acquireAndPersistManagedProotLease(")
        val processAt = source.indexOf("ProcessBuilder(config.command)")

        assertTrue(gateAt >= 0)
        assertTrue(admissionAt > gateAt)
        assertTrue(processAt > admissionAt)
    }

    @Test
    fun `gate contract contains no product resource command or identity exception`() {
        val source = File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundManagedProotProductionGate.kt"
        ).readText()

        listOf("openclaw", "kite.openclaw", "runtimeId", "command", "resourceId", "agentId")
            .forEach { forbidden ->
                assertFalse(source.contains(forbidden, ignoreCase = true))
            }
    }
}
