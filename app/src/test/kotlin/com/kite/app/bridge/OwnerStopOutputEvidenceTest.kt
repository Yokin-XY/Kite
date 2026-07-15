package com.kite.app.bridge

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
    fun `owner 缺失不能由空 remaining 冒充成功`() {
        val output = """
            __kite_owner_stop_outcome:OWNER_NOT_FOUND
            __kite_stop_remaining:
            __kite_stop_remaining_pgid:
        """.trimIndent()

        assertFalse(OwnerStopOutputEvidence.isConfirmed(output))
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
}
