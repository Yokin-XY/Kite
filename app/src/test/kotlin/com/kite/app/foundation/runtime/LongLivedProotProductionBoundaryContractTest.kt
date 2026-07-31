package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF840 no-go 门：统一容量和 provisional lease 未完成前，不得把规划模拟器冒充生产接入。 */
class LongLivedProotProductionBoundaryContractTest {
    @Test
    fun `background runtime does not instantiate the planned long lived controller`() {
        val hostSource = File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt"
        ).readText()
        val recordSource = File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeModels.kt"
        ).readText()

        assertFalse(hostSource.contains("LongLivedProotAdmissionSimulator"))
        assertFalse(hostSource.contains("LongLivedProotOwnerLeaseTransitions"))
        assertFalse(recordSource.contains("longLivedProotLeaseGeneration"))
        assertFalse(recordSource.contains("longLivedProotLeasePhase"))
    }

    @Test
    fun `short task actual controller is not misreported as a unified long lived arbiter`() {
        val coordinatorSource = File(
            "src/main/kotlin/com/kite/app/foundation/runtime/WarmProotRunnerPool.kt"
        ).readText()
        val planningProjectionSource = File(
            "src/main/kotlin/com/kite/app/foundation/runtime/LongLivedProotPlanningHealthProjection.kt"
        ).readText()

        assertTrue(coordinatorSource.contains("private val admission = ProotJobAdmissionController()"))
        assertFalse(coordinatorSource.contains("LongLivedProotAdmissionSimulator"))
        assertTrue(planningProjectionSource.contains("proot_long_planned_"))
        assertTrue(planningProjectionSource.contains("planned_not_production"))
        assertFalse(planningProjectionSource.contains("proot_long_actual_"))
    }
}
