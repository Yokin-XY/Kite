package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF930 门：后台只允许桥接同一个 actual controller，规划模拟器永不进入生产。 */
class LongLivedProotProductionBoundaryContractTest {
    @Test
    fun `background runtime uses persisted checkpoints without the planned controller`() {
        val hostSource = File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt"
        ).readText()
        val recordSource = File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeModels.kt"
        ).readText()

        assertFalse(hostSource.contains("LongLivedProotAdmissionSimulator"))
        assertFalse(hostSource.contains("LongLivedProotOwnerLeaseTransitions"))
        assertTrue(hostSource.contains("WarmProotExecutionCoordinator.acquireManagedOwnerBlocking"))
        assertTrue(hostSource.contains("WarmProotExecutionCoordinator.restoreManagedOwner"))
        assertTrue(hostSource.contains("beginLongLivedProotLease"))
        assertTrue(hostSource.contains("transitionLongLivedProotLease"))
        assertTrue(recordSource.contains("longLivedProotLeaseGeneration"))
        assertTrue(recordSource.contains("longLivedProotLeasePhase"))
        assertTrue(recordSource.contains("longLivedProotLeaseUpdatedAt"))
    }

    @Test
    fun `short and long holders share the actual controller without renaming planned health`() {
        val coordinatorSource = File(
            "src/main/kotlin/com/kite/app/foundation/runtime/WarmProotRunnerPool.kt"
        ).readText()
        val planningProjectionSource = File(
            "src/main/kotlin/com/kite/app/foundation/runtime/LongLivedProotPlanningHealthProjection.kt"
        ).readText()

        assertTrue(coordinatorSource.contains("private val admission = ProotJobAdmissionController()"))
        assertTrue(coordinatorSource.contains("ManagedProotOwnerAdmissionRegistry(admission)"))
        assertFalse(coordinatorSource.contains("LongLivedProotAdmissionSimulator"))
        assertTrue(planningProjectionSource.contains("proot_long_planned_"))
        assertTrue(planningProjectionSource.contains("planned_not_production"))
        assertFalse(planningProjectionSource.contains("proot_long_actual_"))
    }
}
