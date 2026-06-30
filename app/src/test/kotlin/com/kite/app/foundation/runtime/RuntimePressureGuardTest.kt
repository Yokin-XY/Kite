package com.kite.app.foundation.runtime

import com.kite.app.foundation.service.RuntimeRetentionClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePressureGuardTest {
    @Test
    fun evaluate_usesPolicyBudgetAndOnlyRunningRoots() {
        val snapshot = RuntimePressureGuard.evaluate(
            roots = listOf(
                runningRoot(
                    title = "interactive",
                    observedPid = 101,
                    rssKb = 400L,
                    retentionClass = RuntimeRetentionClass.INTERACTIVE,
                    autoReclaimAllowed = false
                ),
                runningRoot(
                    title = "batch",
                    observedPid = 102,
                    rssKb = 500L,
                    retentionClass = RuntimeRetentionClass.BATCH,
                    autoReclaimAllowed = true
                ),
                RuntimeRootSnapshot(
                    ownerKind = RuntimeRootOwnerKind.CARD,
                    ownerId = "stale",
                    title = "stale",
                    statusLabel = "stale",
                    observedPid = null,
                    rssKb = 2_000L,
                    reality = RuntimeRootReality.STALE_RECORD
                )
            ),
            reclaimerPolicy = testPolicy()
        )

        assertEquals(RuntimePressureLevel.CRITICAL, snapshot.level)
        assertEquals(900L, snapshot.totalRssKb)
        assertEquals(400L, snapshot.protectedRssKb)
        assertEquals(500L, snapshot.reclaimableRssKb)
        assertEquals(1, snapshot.candidateCount)
        assertEquals("batch", snapshot.candidates.single().title)
        assertTrue(snapshot.pressureBasis.startsWith("policy_budget"))
    }

    private fun runningRoot(
        title: String,
        observedPid: Int,
        rssKb: Long,
        retentionClass: RuntimeRetentionClass,
        autoReclaimAllowed: Boolean
    ): RuntimeRootSnapshot {
        return RuntimeRootSnapshot(
            ownerKind = RuntimeRootOwnerKind.CARD,
            ownerId = title,
            title = title,
            statusLabel = "running",
            observedPid = observedPid,
            processCount = 1,
            rssKb = rssKb,
            retentionClass = retentionClass,
            autoReclaimAllowed = autoReclaimAllowed,
            classificationSource = "test",
            reality = RuntimeRootReality.OBSERVED
        )
    }

    private fun testPolicy(): RuntimeReclaimerPolicy {
        return RuntimeReclaimerPolicy(
            activeProfile = RuntimeReclaimerProfile.BALANCED,
            memoryPressure = RuntimeMemoryPressurePolicy(
                memoryBudgetKb = 1_000L,
                elevatedRssPercent = 50,
                highRssPercent = 70,
                criticalRssPercent = 85,
                elevatedHostAvailableKb = 0L,
                highHostAvailableKb = 0L,
                criticalHostAvailableKb = 0L
            )
        )
    }
}
