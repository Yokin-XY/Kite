package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ProotOwnerTerminationDecisionTest {
    @Test
    fun `执行窗口结束时目标已经为空必须确认停止`() {
        assertEquals(
            ProotOwnerTerminationOutcome.CONFIRMED,
            ProotOwnerTerminationDecision.finalOutcome(
                remainingTraceePids = emptyList(),
                remainingProcessGroupIds = emptyList()
            )
        )
    }

    @Test
    fun `活动注册表旧条目不能覆盖强身份目标已经消失的事实`() {
        // 活动注册表只负责发现待核验目标。目标经过 starttime 强身份核验后已经消失，
        // 即使注册表文件尚未来得及删除旧条目，也必须直接确认停止。
        assertEquals(
            ProotOwnerTerminationOutcome.CONFIRMED,
            ProotOwnerTerminationDecision.finalOutcome(
                remainingTraceePids = emptyList(),
                remainingProcessGroupIds = emptyList()
            )
        )
    }

    @Test
    fun `仍有强身份目标时才报告仍在运行`() {
        assertEquals(
            ProotOwnerTerminationOutcome.STILL_RUNNING,
            ProotOwnerTerminationDecision.finalOutcome(
                remainingTraceePids = listOf(42),
                remainingProcessGroupIds = listOf(42)
            )
        )
    }
}
