package com.kite.app.feature.runtimemanagement

import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagementCoordinator
import com.kite.app.application.runtimemanagement.RuntimeManagementDispatchResult
import com.kite.app.application.runtimemanagement.RuntimeManagementGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeManagementFeatureControllerTest {
    @Test
    fun `submit process action immediately projects awaiting confirmation`() = runTest {
        val gateway = FakeGateway(snapshot())
        val controller = controller(gateway)
        val action = controller.state.value.runs.single().childProcesses.single().stopAction!!

        val effect = controller.dispatch(RuntimeManagementFeatureAction.Submit(action))

        assertNull(effect)
        assertEquals(listOf("process-52" to 52), gateway.endedProcesses)
        val projected = controller.state.value.runs.single().childProcesses.single()
        assertEquals("结束中", projected.stopAction?.label)
        assertEquals(CardRunStatus.Running, controller.state.value.runs.single().status)
    }

    @Test
    fun `open surface returns shell effect without submitting runtime command`() = runTest {
        val gateway = FakeGateway(snapshot())
        val controller = controller(gateway)
        val action = controller.state.value.runs.single().surfaces.single().openAction

        val effect = controller.dispatch(RuntimeManagementFeatureAction.Submit(action))

        assertTrue(effect is RuntimeManagementFeatureEffect.OpenSurface)
        assertTrue(gateway.endedProcesses.isEmpty())
    }

    private fun controller(gateway: FakeGateway): RuntimeManagementFeatureController {
        val coordinator = RuntimeManagementCoordinator(
            gateway = gateway,
            stopRun = { RuntimeManagementDispatchResult.accepted("run_stop_requested") }
        )
        return RuntimeManagementFeatureController(gateway, coordinator)
    }

    private fun snapshot() = RuntimeManagementSnapshot(
        runs = listOf(
            CardRunState(
                instanceId = "run-1",
                recipeId = "recipe-1",
                recipeName = "OpenClaw",
                status = CardRunStatus.Running,
                surface = com.kite.app.run.CardRunSurface.Report,
                runtimeRootOwnerId = "card:run-1@10",
                runtimeOwnerId = "card:run-1@10/step/0-shell/attempt/1",
                runtimeUnitId = "card:run-1@10/step/0-shell/attempt/1",
                ownedRuntimeOwnerIds = listOf("card:run-1@10/step/0-shell/attempt/1"),
                rootPid = "41",
                lastMeaningfulOutput = "running",
                createdAt = 10L,
                updatedAt = 10L
            )
        ),
        processes = listOf(
            RuntimeManagedProcess(
                id = "process-52",
                pid = 52,
                parentPid = 41,
                title = "child",
                stateLabel = "运行中",
                ownerId = "card:run-1@10/step/0-shell/attempt/1",
                canEndDirectly = true
            )
        )
    )

    private class FakeGateway(initial: RuntimeManagementSnapshot) : RuntimeManagementGateway {
        private val mutableSnapshots = MutableStateFlow(initial)
        override val snapshots = mutableSnapshots
        val endedProcesses = mutableListOf<Pair<String, Int>>()

        override fun currentSnapshot(): RuntimeManagementSnapshot = mutableSnapshots.value

        override fun refresh(force: Boolean) = Unit

        override suspend fun endTerminal(sessionId: String) = RuntimeManagementDispatchResult.accepted()

        override suspend fun endProcess(processId: String, pid: Int): RuntimeManagementDispatchResult {
            endedProcesses += processId to pid
            return RuntimeManagementDispatchResult.accepted("process_end_requested")
        }

        override suspend fun stopBackgroundRuntime(runtimeId: String) = RuntimeManagementDispatchResult.accepted()

        override suspend fun restartBackgroundRuntime(runtimeId: String) = RuntimeManagementDispatchResult.accepted()
    }
}
