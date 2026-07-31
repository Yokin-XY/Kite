package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF920 门：检查点可以持久化，但 RF930 前不得把规划模拟器或后台 Host 冒充生产接入。 */
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
        assertFalse(hostSource.contains("beginLongLivedProotLease"))
        assertFalse(hostSource.contains("transitionLongLivedProotLease"))
        assertTrue(recordSource.contains("longLivedProotLeaseGeneration"))
        assertTrue(recordSource.contains("longLivedProotLeasePhase"))
        assertTrue(recordSource.contains("longLivedProotLeaseUpdatedAt"))
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
