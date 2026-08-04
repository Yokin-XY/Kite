package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.RuntimeExposureScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRuntimeSpacePolicyTest {

    @Test
    fun `background runtime can only start in its active space`() {
        assertTrue(BackgroundRuntimeSpacePolicy.mayStart("space-main", "space-main"))
        assertFalse(
            BackgroundRuntimeSpacePolicy.mayStart(
                "space-main",
                "space-environment-profile_2",
            )
        )
    }

    @Test
    fun `confirmed stop clears active process and deferred restart state`() {
        val running = record().copy(
            status = BackgroundRuntimeStatus.RUNNING,
            healthStatus = BackgroundRuntimeHealthStatus.HEALTHY,
            pid = 456,
            lastAdmissionDeferredAt = 12L,
            lastAdmissionSource = "recovery",
            lastAdmissionReason = "waiting",
        )

        val stopped = BackgroundRuntimeSpacePolicy.confirmedStopped(running, 99L)

        assertEquals(BackgroundRuntimeStatus.STOPPED, stopped.status)
        assertEquals(BackgroundRuntimeHealthStatus.INACTIVE, stopped.healthStatus)
        assertNull(stopped.pid)
        assertEquals(99L, stopped.lastStoppedAt)
        assertNull(stopped.lastAdmissionDeferredAt)
        assertNull(stopped.lastAdmissionSource)
        assertNull(stopped.lastAdmissionReason)
    }

    @Test
    fun `registered record remains registered when environment stops`() {
        val registered = record()

        val stopped = BackgroundRuntimeSpacePolicy.confirmedStopped(registered, 99L)

        assertEquals(BackgroundRuntimeStatus.REGISTERED, stopped.status)
        assertNull(stopped.lastStoppedAt)
    }

    @Test
    fun `automatic start stops after shell reports command unavailable`() {
        assertTrue(
            BackgroundRuntimeRestartGate.blocksAutomaticStart(record().copy(lastExitCode = 126))
        )
        assertTrue(
            BackgroundRuntimeRestartGate.blocksAutomaticStart(record().copy(lastExitCode = 127))
        )
        assertFalse(
            BackgroundRuntimeRestartGate.blocksAutomaticStart(record().copy(lastExitCode = 1))
        )
    }

    @Test
    fun `core supervisor retries after its packaged dependency is restored`() {
        val core = record().copy(
            kind = BackgroundRuntimeKind.CONTAINER_SUPERVISOR,
            lastExitCode = 127,
        )

        assertTrue(BackgroundRuntimeRestartGate.blocksAutomaticStart(core))
        assertFalse(
            BackgroundRuntimeRestartGate.blocksAutomaticStart(
                record = core,
                coreDependencyRestored = true,
            )
        )
        assertTrue(
            BackgroundRuntimeRestartGate.blocksAutomaticStart(
                record = record().copy(lastExitCode = 127),
                coreDependencyRestored = true,
            )
        )
    }

    @Test
    fun `new active attempt clears stale command unavailable exit`() {
        assertNull(
            BackgroundRuntimeStatusTransitionPolicy.nextExitCode(
                previousExitCode = 127,
                observedExitCode = null,
                nextStatus = BackgroundRuntimeStatus.STARTING,
            )
        )
        assertEquals(
            127,
            BackgroundRuntimeStatusTransitionPolicy.nextExitCode(
                previousExitCode = 127,
                observedExitCode = null,
                nextStatus = BackgroundRuntimeStatus.ERROR,
            )
        )
        assertEquals(
            1,
            BackgroundRuntimeStatusTransitionPolicy.nextExitCode(
                previousExitCode = 127,
                observedExitCode = 1,
                nextStatus = BackgroundRuntimeStatus.RUNNING,
            )
        )
    }

    @Test
    fun `builtin supervisor reports missing dependency with standard exit code`() {
        val command = BackgroundRuntimeRegistry.builtinContainerSupervisorStartCommand()

        assertTrue(command.contains("test -x /usr/bin/supervisord"))
        assertTrue(command.contains("test -f /etc/supervisor/supervisord.conf"))
        assertTrue(command.contains("grep -Fq 'KFShell runs Android/proot without systemd'"))
        assertTrue(command.contains("rm -f /workspace/.kf/bin/systemctl"))
        assertTrue(command.contains("rm -f /workspace/.kf/bin/service"))
        assertTrue(command.contains("exit 127"))
        assertTrue(command.contains("exec /usr/bin/supervisord"))
    }

    @Test
    fun `diagnostic capacity workers are never resident by default`() {
        assertEquals(
            RuntimeRetentionClass.EPHEMERAL,
            BackgroundRuntimeRegistry.prootCapacityWorkerRetentionClass(1)
        )
        assertEquals(
            RuntimeRetentionClass.EPHEMERAL,
            BackgroundRuntimeRegistry.prootCapacityWorkerRetentionClass(2)
        )
    }

    private fun record() = BackgroundRuntimeRecord(
        id = "runtime",
        spaceId = "space-main",
        kind = BackgroundRuntimeKind.CUSTOM,
        mode = BackgroundRuntimeMode.PROCESS,
        title = "Runtime",
        workingDirectory = "/workspace",
        startCommand = "sleep 60",
        exposureScope = RuntimeExposureScope.LOOPBACK_ONLY,
        logPath = "/tmp/runtime.log",
        createdAt = 1L,
    )
}
