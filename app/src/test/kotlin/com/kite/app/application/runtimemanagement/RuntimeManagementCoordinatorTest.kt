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
    fun `stopped root confirms while old generation process continues background cleanup`() = runTest {
        val owner = "card:run-1@10/step/0-shell/attempt/1"
        val running = run(CardRunStatus.Running, owner = owner)
        val ownedProcess = process(ownerId = owner)
        val gateway = FakeGateway(snapshot(runs = listOf(running), processes = listOf(ownedProcess)))
        val coordinator = coordinator(gateway)

        coordinator.submit(RuntimeManagementCommand.StopRun("run-1"))
        val residue = snapshot(
            runs = listOf(run(CardRunStatus.Stopped, owner = owner)),
            processes = listOf(ownedProcess)
        )
        gateway.publish(residue)
        coordinator.reconcile(residue)

        assertTrue(coordinator.commands.value.isEmpty())
    }

    @Test
    fun `old generation residue does not retain mutation after same instance starts again`() = runTest {
        val oldOwner = "card:run-1@10/step/0-shell/attempt/1"
        val newOwner = "card:run-1@20/step/0-shell/attempt/2"
        val oldProcess = process(ownerId = oldOwner)
        val gateway = FakeGateway(
            snapshot(
                runs = listOf(run(CardRunStatus.Running, owner = oldOwner, generation = 10L)),
                processes = listOf(oldProcess)
            )
        )
        val coordinator = coordinator(gateway)

        coordinator.submit(RuntimeManagementCommand.StopRun("run-1"))
        val nextGeneration = snapshot(
            runs = listOf(run(CardRunStatus.Running, owner = newOwner, generation = 20L)),
            processes = listOf(oldProcess)
        )
        gateway.publish(nextGeneration)
        coordinator.reconcile(nextGeneration)

        assertTrue(coordinator.commands.value.isEmpty())
    }

    @Test
    fun `new generation child does not block old subtree confirmation`() = runTest {
        val root = run(CardRunStatus.Running, generation = 10L)
        val oldChild = CardRunState(
            instanceId = "child-1",
            recipeId = root.recipeId,
            parentInstanceId = root.instanceId,
            status = CardRunStatus.Running,
            createdAt = 11L,
            updatedAt = 11L
        )
        val gateway = FakeGateway(snapshot(runs = listOf(root, oldChild)))
        val coordinator = coordinator(gateway)

        coordinator.submit(RuntimeManagementCommand.StopRun("run-1"))
        val stoppedWithNewChild = snapshot(
            runs = listOf(
                root.copy(status = CardRunStatus.Stopped),
                oldChild.copy(createdAt = 21L, updatedAt = 21L)
            )
        )
        gateway.publish(stoppedWithNewChild)
        coordinator.reconcile(stoppedWithNewChild)

        assertTrue(coordinator.commands.value.isEmpty())
    }

    @Test
    fun `missing root does not confirm while captured descendant fact remains`() = runTest {
        val root = run(CardRunStatus.Running)
        val child = CardRunState(
            instanceId = "child-1",
            recipeId = root.recipeId,
            parentInstanceId = root.instanceId,
            status = CardRunStatus.Running,
            createdAt = 11L,
            updatedAt = 11L
        )
        val gateway = FakeGateway(snapshot(runs = listOf(root, child)))
        val coordinator = coordinator(gateway)

        coordinator.submit(RuntimeManagementCommand.StopRun("run-1"))
        val childRemains = snapshot(runs = listOf(child))
        gateway.publish(childRemains)
        coordinator.reconcile(childRemains)

        assertTrue(coordinator.commands.value.containsKey("run:run-1"))

        val closed = snapshot()
        gateway.publish(closed)
        coordinator.reconcile(closed)
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

    private fun run(
        status: CardRunStatus,
        owner: String? = null,
        generation: Long = 10L
    ) = CardRunState(
        instanceId = "run-1",
        recipeId = "recipe-1",
        status = status,
        runtimeRootOwnerId = owner,
        runtimeOwnerId = owner,
        runtimeUnitId = owner,
        ownedRuntimeOwnerIds = listOfNotNull(owner),
        createdAt = generation,
        updatedAt = generation
    )

    private fun process(ownerId: String? = null) = RuntimeManagedProcess(
        id = "process-52",
        pid = 52,
        title = "child",
        stateLabel = "运行中",
        ownerId = ownerId,
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
