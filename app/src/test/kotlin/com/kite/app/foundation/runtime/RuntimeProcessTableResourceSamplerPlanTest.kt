package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProcessTableResourceSamplerPlanTest {
    @Test
    fun `sampler is a fixed shared write probe with bounded output and timeout`() {
        val plan = RuntimeProcessTableResourceSampler.executionPlanForTests(
            reason = "low_density_memory_sample_tick",
            jobId = "sampler-1",
        )

        assertEquals("sampler-1", plan.admission.jobId)
        assertEquals("system:runtime-process-resource-sampler", plan.admission.ownerId)
        assertEquals(RuntimeLaneKind.PROBE, plan.admission.lane)
        assertEquals(ProotJobAccess.SHARED_WRITE, plan.admission.access)
        assertEquals(ProotJobCancellationMode.TIMEOUT_AND_OWNER, plan.admission.cancellationMode)
        assertEquals(ProotJobResultMode.CAPTURED_STDIO, plan.admission.resultMode)
        assertFalse(plan.admission.pressureEssential)
        assertEquals(plan.admission.jobId, plan.job.jobId)
        assertEquals(
            listOf("/workspace/.kf/system/bin/kf-resource-sampler", "--update-table"),
            plan.job.argv,
        )
        assertEquals("/workspace", plan.job.workingDirectory)
        assertEquals(20_000L, plan.job.timeoutMs)
        assertEquals(64 * 1024, plan.job.maxOutputBytesPerStream)
    }

    @Test
    fun `memory pressure sample remains admissible while optional probe may defer`() {
        val pressure = RuntimeProcessTableResourceSampler.executionPlanForTests(
            reason = "memory_pressure_resource_sample",
            jobId = "sampler-pressure",
        )
        val normal = RuntimeProcessTableResourceSampler.executionPlanForTests(
            reason = "low_density_memory_sample_tick",
            jobId = "sampler-normal",
        )

        assertTrue(pressure.admission.pressureEssential)
        assertFalse(normal.admission.pressureEssential)
    }
}
