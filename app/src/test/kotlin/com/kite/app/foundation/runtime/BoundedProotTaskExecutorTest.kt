package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedProotTaskExecutorTest {
    @Test
    fun `bounded task maps one identity into admission and runner job`() {
        val plan = BoundedProotTaskExecutor.plan(
            BoundedProotTaskRequest(
                jobId = "bounded-1",
                ownerId = "system:bounded-test",
                argv = listOf("/usr/bin/printf", "ok"),
                workingDirectory = "/workspace",
                environment = mapOf("LANG" to "C.UTF-8"),
                lane = RuntimeLaneKind.PROBE,
                access = ProotJobAccess.READ_ONLY,
                timeoutMs = 2_000L,
                maxOutputBytesPerStream = 4_096,
            )
        )

        assertEquals("bounded-1", plan.admission.jobId)
        assertEquals(plan.admission.jobId, plan.job.jobId)
        assertEquals("system:bounded-test", plan.admission.ownerId)
        assertEquals(ProotJobCancellationMode.TIMEOUT_AND_OWNER, plan.admission.cancellationMode)
        assertEquals(ProotJobResultMode.CAPTURED_STDIO, plan.admission.resultMode)
        assertEquals(RuntimeLaneKind.PROBE, plan.admission.lane)
        assertEquals(ProotJobAccess.READ_ONLY, plan.admission.access)
        assertEquals(listOf("/usr/bin/printf", "ok"), plan.job.argv)
        assertEquals(mapOf("LANG" to "C.UTF-8"), plan.job.environment)
        assertEquals(2_000L, plan.job.timeoutMs)
        assertEquals(4_096, plan.job.maxOutputBytesPerStream)
    }

    @Test
    fun `interactive and unbounded requests fail before admission`() {
        val base = BoundedProotTaskRequest(
            jobId = "bounded-invalid",
            ownerId = "system:bounded-test",
            argv = listOf("/bin/true"),
            lane = RuntimeLaneKind.PROBE,
            access = ProotJobAccess.READ_ONLY,
        )

        assertEquals(
            "bounded_interactive_lane_not_allowed",
            assertThrows(IllegalArgumentException::class.java) {
                BoundedProotTaskExecutor.plan(base.copy(lane = RuntimeLaneKind.INTERACTIVE))
            }.message,
        )
        assertEquals(
            "bounded_runtime_timeout_invalid",
            assertThrows(IllegalArgumentException::class.java) {
                BoundedProotTaskExecutor.plan(base.copy(timeoutMs = 120_001L))
            }.message,
        )
        assertEquals(
            "bounded_output_limit_invalid",
            assertThrows(IllegalArgumentException::class.java) {
                BoundedProotTaskExecutor.plan(base.copy(maxOutputBytesPerStream = 1024 * 1024 + 1))
            }.message,
        )
    }

    @Test
    fun `shell text is not a bounded task input shape`() {
        val fields = BoundedProotTaskRequest::class.java.declaredFields.map { it.name }.toSet()

        assertEquals(false, "command" in fields)
        assertEquals(false, "shell" in fields)
        assertEquals(true, "argv" in fields)
    }
}
