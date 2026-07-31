package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.ProotJobAccess
import com.kite.app.foundation.runtime.RuntimeLaneKind
import com.kite.app.foundation.runtime.WarmProotExecutionRoute
import com.kite.app.foundation.runtime.WarmProotJobExecution
import com.kite.app.foundation.runtime.WarmProotPoolExecution
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SupervisordServiceHealthRunnerTest {
    @Test
    fun `health request is one fixed helper argv and shared write service lane`() {
        val request = buildSupervisordHealthTaskRequest(
            jobId = "supervisord-health-1",
            workingDirectory = "/workspace",
        )

        assertEquals(listOf(WorkspaceBuildSupport.CONTAINER_SUPERVISORD_HEALTH_SNAPSHOT_PATH), request.argv)
        assertEquals("system:supervisord-health", request.ownerId)
        assertEquals(RuntimeLaneKind.SERVICE, request.lane)
        assertEquals(ProotJobAccess.SHARED_WRITE, request.access)
        assertEquals(5_000L, request.waitTimeoutMs)
        assertEquals(10_000L, request.timeoutMs)
        assertEquals(emptyMap<String, String>(), request.environment)
        assertFalse(request.pressureEssential)
    }

    @Test
    fun `successful helper output keeps status and log marker contract`() {
        val output = "gateway RUNNING pid 42, uptime 0:01:00\n__KF_SUPERVISOR_LOGS__\n"
        val mapped = execution(
            WarmProotExecutionRoute.WARM_RUNNER,
            WarmProotJobExecution(
                jobId = "health-ok",
                started = true,
                exitCode = 0,
                termSignal = 0,
                stdoutTail = output.toByteArray(),
            ),
        ).toSupervisordHealthCommandResult()

        assertEquals(0, mapped.exitCode)
        assertEquals(output, mapped.output)
    }

    @Test
    fun `prestart fallback success keeps the same command result`() {
        val mapped = execution(
            WarmProotExecutionRoute.INDEPENDENT_FALLBACK,
            WarmProotJobExecution(
                jobId = "health-fallback",
                started = true,
                exitCode = 0,
                termSignal = 0,
                stdoutTail = "__KF_SUPERVISOR_LOGS__\n".toByteArray(),
            ),
        ).toSupervisordHealthCommandResult()

        assertEquals(0, mapped.exitCode)
        assertEquals("__KF_SUPERVISOR_LOGS__\n", mapped.output)
    }

    @Test
    fun `rejection failure and truncation remain explicit failures`() {
        val rejected = execution(
            WarmProotExecutionRoute.ADMISSION_REJECTED,
            null,
            reason = "admission_shared_write_waiting_for_exclusive",
        ).toSupervisordHealthCommandResult()
        val truncated = execution(
            WarmProotExecutionRoute.WARM_RUNNER,
            WarmProotJobExecution(
                jobId = "health-truncated",
                started = true,
                exitCode = 0,
                termSignal = 0,
                stdoutDroppedBytes = 5L,
            ),
        ).toSupervisordHealthCommandResult()

        assertEquals(-1, rejected.exitCode)
        assertEquals("admission_shared_write_waiting_for_exclusive", rejected.output)
        assertEquals(-1, truncated.exitCode)
        assertEquals("supervisor status output truncated: stdout=5 stderr=0", truncated.output)
    }

    private fun execution(
        route: WarmProotExecutionRoute,
        execution: WarmProotJobExecution?,
        reason: String = route.name.lowercase(),
    ) = WarmProotPoolExecution(
        route = route,
        execution = execution,
        reason = reason,
    )
}
