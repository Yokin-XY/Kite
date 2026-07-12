package com.kite.app.application.runtimemanagement

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeManagementCoordinatorTest {
    @Test
    fun `stop run waits for stopped fact before clearing mutation`() = runTest {
        val gateway = FakeGateway(snapshot(runs = listOf(run(CardRunStatus.Running))))
        val coordinator = coordinator(gateway)

        val result = coordinator.submit(RuntimeManagementCommand.StopRun("run-1"))

        assertTrue(result is RuntimeManagementSubmitResult.Accepted)
        assertEquals(
            RuntimeManagementCommandPhase.AwaitingConfirmation,
            coordinator.commands.value.getValue("run:run-1").phase
        )
        assertEquals(CardRunStatus.Running, gateway.currentSnapshot().runs.single().status)

        val stopped = snapshot(runs = listOf(run(CardRunStatus.Stopped)))
        gateway.publish(stopped)
        coordinator.reconcile(stopped)

        assertTrue(coordinator.commands.value.isEmpty())
    }

    @Test
    fun `end child process never mutates card state and completes only after pid disappears`() = runTest {
        val process = process()
        val gateway = FakeGateway(snapshot(runs = listOf(run(CardRunStatus.Running)), processes = listOf(process)))
        val coordinator = coordinator(gateway)

        coordinator.submit(RuntimeManagementCommand.EndProcess("process-52", 52))

        assertEquals(listOf("process-52" to 52), gateway.endedProcesses)
        assertEquals(CardRunStatus.Running, gateway.currentSnapshot().runs.single().status)
        assertEquals(
            RuntimeManagementCommandPhase.AwaitingConfirmation,
            coordinator.commands.value.getValue("process:process-52").phase
        )

        val confirmed = snapshot(runs = gateway.currentSnapshot().runs)
        gateway.publish(confirmed)
        coordinator.reconcile(confirmed)

        assertTrue(coordinator.commands.value.isEmpty())
    }

    @Test
    fun `timeout becomes visible failure instead of silent reset`() = runTest {
        var now = 100L
        val gateway = FakeGateway(snapshot(processes = listOf(process())))
        val coordinator = RuntimeManagementCoordinator(
            gateway = gateway,
            stopRun = { RuntimeManagementDispatchResult.accepted() },
            clock = { now },
            confirmationTimeoutMs = 50L
        )

        coordinator.submit(RuntimeManagementCommand.EndProcess("process-52", 52))
        now = 151L
        coordinator.reconcile(gateway.currentSnapshot(), now)

        val failed = coordinator.commands.value.getValue("process:process-52")
        assertEquals(RuntimeManagementCommandPhase.Failed, failed.phase)
        assertTrue(failed.message.contains("确认"))
    }

    @Test
    fun `rejected dispatch records failure and allows explicit dismiss`() = runTest {
        val gateway = FakeGateway(snapshot(processes = listOf(process()))).apply {
            processDispatch = RuntimeManagementDispatchResult.rejected("permission_denied")
        }
        val coordinator = coordinator(gateway)

        val first = coordinator.submit(RuntimeManagementCommand.EndProcess("process-52", 52))

        assertTrue(first is RuntimeManagementSubmitResult.Ignored)
        assertEquals(
            RuntimeManagementCommandPhase.Failed,
            coordinator.commands.value.getValue("process:process-52").phase
        )
        coordinator.dismissFailure("process:process-52")
        assertFalse(coordinator.commands.value.containsKey("process:process-52"))
    }

    @Test
    fun `duplicate pending command is ignored`() = runTest {
        val gateway = FakeGateway(snapshot(processes = listOf(process())))
        val coordinator = coordinator(gateway)

        coordinator.submit(RuntimeManagementCommand.EndProcess("process-52", 52))
        val duplicate = coordinator.submit(RuntimeManagementCommand.EndProcess("process-52", 52))

        assertEquals(RuntimeManagementSubmitResult.Ignored("already_pending"), duplicate)
        assertEquals(1, gateway.endedProcesses.size)
    }

    private fun coordinator(gateway: FakeGateway): RuntimeManagementCoordinator =
        RuntimeManagementCoordinator(
            gateway = gateway,
            stopRun = { RuntimeManagementDispatchResult.accepted("run_stop_requested") }
        )

    private fun snapshot(
        runs: List<CardRunState> = emptyList(),
        processes: List<RuntimeManagedProcess> = emptyList()
    ) = RuntimeManagementSnapshot(runs = runs, processes = processes, refreshedAt = 100L)

    private fun run(status: CardRunStatus) = CardRunState(
        instanceId = "run-1",
        recipeId = "recipe-1",
        status = status
    )

    private fun process() = RuntimeManagedProcess(
        id = "process-52",
        pid = 52,
        title = "child",
        stateLabel = "运行中",
        canEndDirectly = true
    )

    private class FakeGateway(initial: RuntimeManagementSnapshot) : RuntimeManagementGateway {
        private val mutableSnapshots = MutableStateFlow(initial)
        override val snapshots = mutableSnapshots
        val endedProcesses = mutableListOf<Pair<String, Int>>()
        var processDispatch = RuntimeManagementDispatchResult.accepted("process_end_requested")

        override fun currentSnapshot(): RuntimeManagementSnapshot = mutableSnapshots.value

        override fun refresh(force: Boolean) = Unit

        override suspend fun endTerminal(sessionId: String) =
            RuntimeManagementDispatchResult.accepted("terminal_end_requested")

        override suspend fun endProcess(processId: String, pid: Int): RuntimeManagementDispatchResult {
            endedProcesses += processId to pid
            return processDispatch
        }

        override suspend fun stopBackgroundRuntime(runtimeId: String) =
            RuntimeManagementDispatchResult.accepted("runtime_stop_requested")

        override suspend fun restartBackgroundRuntime(runtimeId: String) =
            RuntimeManagementDispatchResult.accepted("runtime_restart_requested")

        fun publish(snapshot: RuntimeManagementSnapshot) {
            mutableSnapshots.value = snapshot
        }
    }
}
