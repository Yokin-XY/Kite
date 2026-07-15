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
    fun `解析错误仍禁止破坏性动作`() {
        val now = 10_000L
        val parseError = ProotTelemetrySnapshot(
            collectionStatus = "loaded",
            fileExists = true,
            refreshedAtMs = now,
            counters = ProotTelemetryCounters(totalEvents = 5L, parseErrors = 1L)
        )

        assertFalse(ProotOwnerTerminationEvidence.readiness(parseError, now).usable)
    }

    @Test
    fun `历史裁剪只阻止覆盖区间之前的 owner`() {
        val now = 10_000L
        val snapshot = ProotTelemetrySnapshot(
            collectionStatus = "loaded",
            fileExists = true,
            refreshedAtMs = now,
            ownerEvidenceCompleteFromMs = 5_000L,
            ownerEvidenceCoverageReason = "historical_tail_skipped",
            counters = ProotTelemetryCounters(totalEvents = 5L, skippedBytes = 20L)
        )

        assertTrue(ProotOwnerTerminationEvidence.readiness(snapshot, now).usable)
        assertTrue(
            ProotOwnerTerminationEvidence.readiness(
                snapshot,
                now,
                ownerId = "card:new@6000/step/0-start/attempt/1"
            ).usable
        )
        assertFalse(
            ProotOwnerTerminationEvidence.readiness(
                snapshot,
                now,
                ownerId = "card:old@4000/step/0-start/attempt/1"
            ).usable
        )
    }

    @Test
    fun `跳段但没有覆盖边界时仍拒绝 owner 停止`() {
        val now = 10_000L
        val snapshot = ProotTelemetrySnapshot(
            collectionStatus = "loaded",
            fileExists = true,
            refreshedAtMs = now,
            counters = ProotTelemetryCounters(totalEvents = 5L, skippedBytes = 20L)
        )

        assertFalse(
            ProotOwnerTerminationEvidence.readiness(
                snapshot,
                now,
                ownerId = "card:test@6000"
            ).usable
        )
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
    fun `owner 不存在保留底层 outcome 并作为已收敛目标`() {
        val result = ProotOwnerTerminationResult(
            ownerId = "card:test@1",
            outcome = ProotOwnerTerminationOutcome.OWNER_NOT_FOUND,
            reason = "owner_not_observed"
        )
        val output = result.toStopOutput()

        assertTrue(result.settled)
        assertTrue(output.contains("__kite_owner_stop_outcome:OWNER_NOT_FOUND"))
        assertTrue(output.contains("__kite_stop_remaining:"))
    }
}
