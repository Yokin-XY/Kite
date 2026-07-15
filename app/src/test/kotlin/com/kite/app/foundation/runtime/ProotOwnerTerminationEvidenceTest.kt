package com.kite.app.foundation.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotOwnerTerminationEvidenceTest {
    @Test
    fun `完整且刚刷新的来源允许进入关停`() {
        val now = 10_000L
        val snapshot = ProotTelemetrySnapshot(
            collectionStatus = "loaded",
            fileExists = true,
            refreshedAtMs = now,
            counters = ProotTelemetryCounters(totalEvents = 5L)
        )

        assertTrue(ProotOwnerTerminationEvidence.readiness(snapshot, now).usable)
    }

    @Test
    fun `解析错误和跳段都禁止破坏性动作`() {
        val now = 10_000L
        val parseError = ProotTelemetrySnapshot(
            collectionStatus = "loaded",
            fileExists = true,
            refreshedAtMs = now,
            counters = ProotTelemetryCounters(totalEvents = 5L, parseErrors = 1L)
        )
        val skipped = parseError.copy(
            counters = ProotTelemetryCounters(totalEvents = 5L, skippedBytes = 20L)
        )

        assertFalse(ProotOwnerTerminationEvidence.readiness(parseError, now).usable)
        assertFalse(ProotOwnerTerminationEvidence.readiness(skipped, now).usable)
    }

    @Test
    fun `owner 从未出现时空探测不能证明停止`() {
        assertFalse(
            ProotOwnerTerminationEvidence.canConfirm(
                ownerWasObserved = false,
                healthySilentRounds = 2,
                probeReliable = true,
                liveTraceePids = emptyList(),
                liveProcessGroupIds = emptyList()
            )
        )
    }

    @Test
    fun `只有连续健康静默和直接探测为空才确认`() {
        assertFalse(
            ProotOwnerTerminationEvidence.canConfirm(
                ownerWasObserved = true,
                healthySilentRounds = 1,
                probeReliable = true,
                liveTraceePids = emptyList(),
                liveProcessGroupIds = emptyList()
            )
        )
        assertFalse(
            ProotOwnerTerminationEvidence.canConfirm(
                ownerWasObserved = true,
                healthySilentRounds = 2,
                probeReliable = true,
                liveTraceePids = listOf(42),
                liveProcessGroupIds = emptyList()
            )
        )
        assertTrue(
            ProotOwnerTerminationEvidence.canConfirm(
                ownerWasObserved = true,
                healthySilentRounds = 2,
                probeReliable = true,
                liveTraceePids = emptyList(),
                liveProcessGroupIds = emptyList()
            )
        )
    }

    @Test
    fun `未知结果输出显式 outcome 供 Bridge 拒绝假成功`() {
        val output = ProotOwnerTerminationResult(
            ownerId = "card:test@1",
            outcome = ProotOwnerTerminationOutcome.OWNER_NOT_FOUND,
            reason = "owner_not_observed"
        ).toStopOutput()

        assertTrue(output.contains("__kite_owner_stop_outcome:OWNER_NOT_FOUND"))
        assertTrue(output.contains("__kite_stop_remaining:"))
    }
}
