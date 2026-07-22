package com.kite.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerStopOutputEvidenceTest {
    @Test
    fun `确认 outcome 且 PID PGID 均为空才算停止`() {
        val output = """
            __kite_owner_stop_outcome:CONFIRMED
            __kite_stop_remaining:
            __kite_stop_remaining_pgid:
        """.trimIndent()

        assertTrue(OwnerStopOutputEvidence.isConfirmed(output))
    }

    @Test
    fun `owner 不存在且无残留视为目标已经关闭`() {
        val output = """
            __kite_owner_stop_outcome:OWNER_NOT_FOUND
            __kite_stop_remaining:
            __kite_stop_remaining_pgid:
        """.trimIndent()

        assertTrue(OwnerStopOutputEvidence.isConfirmed(output))
        assertEquals("已关闭", OwnerStopOutputEvidence.userMessage(output))
    }

    @Test
    fun `PGID 残留与 PID 残留同样阻止确认`() {
        assertFalse(
            OwnerStopOutputEvidence.isConfirmed(
                "__kite_owner_stop_outcome:CONFIRMED\n__kite_stop_remaining_pgid:88"
            )
        )
        assertFalse(
            OwnerStopOutputEvidence.isConfirmed(
                "__kite_owner_stop_outcome:CONFIRMED\n__kite_stop_remaining:42"
            )
        )
    }

    @Test
    fun `底层遥测失败标记投影为简短用户消息`() {
        val output = """
            __kite_owner_stop_owner:card:test@1
            __kite_owner_stop_outcome:TELEMETRY_UNAVAILABLE
            __kite_owner_stop_reason:telemetry_coverage_unknown
            __kite_owner_stop_targets:
            __kite_stop_remaining:
        """.trimIndent()

        assertEquals("运行记录暂不完整，未执行强制停止", OwnerStopOutputEvidence.userMessage(output))
    }

    @Test
    fun `多个残留 PID 被收敛并显示一次`() {
        val output = """
            __kite_owner_stop_outcome:CONFIRMED
            __kite_stop_remaining:42,43
            __kite_stop_remaining_pgid:43,88
        """.trimIndent()

        assertEquals(listOf("42", "43", "88"), OwnerStopOutputEvidence.remainingProcessIds(output))
        assertEquals("停止后仍有进程残留：42,43,88", OwnerStopOutputEvidence.userMessage(output))
    }

    @Test
    fun `仍在运行 outcome 不会被解释成前端超时`() {
        val output = """
            __kite_owner_stop_outcome:STILL_RUNNING
            __kite_stop_remaining:
        """.trimIndent()

        assertFalse(OwnerStopOutputEvidence.isConfirmed(output))
        assertEquals("停止后仍观测到运行进程", OwnerStopOutputEvidence.userMessage(output))
    }
}
