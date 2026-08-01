package com.kite.app.application.runs

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunInstanceCloseCoordinatorTest {
    @Test
    fun `普通实例按精确代次进入既有停止链`() = runTest {
        val state = state(ownerKind = CardRunState.OWNER_KIND_RESOURCE, generation = 9L)
        val stops = mutableListOf<RunStopCommand>()
        val coordinator = coordinator(this, state, stops = stops)

        val result = coordinator.close(command(state, generation = 9L))

        assertEquals(RunCommandResult.Accepted(state.instanceId), result)
        assertEquals(listOf(RunStopCommand(state.instanceId, 9L)), stops)
    }

    @Test
    fun `安装向导复用资源取消链而不直接调用运行停止`() = runTest {
        val state = state(ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD, generation = 10L)
        val stops = mutableListOf<RunStopCommand>()
        val cancelled = mutableListOf<CardRunState>()
        val coordinator = coordinator(this, state, stops, cancelled)

        val result = coordinator.close(command(state, generation = 10L))

        assertEquals(RunCommandResult.Accepted(state.instanceId), result)
        assertEquals(listOf(state), cancelled)
        assertTrue(stops.isEmpty())
    }

    @Test
    fun `安装向导取消被拒绝时保持实例且不伪报关闭`() = runTest {
        val state = state(ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD, generation = 10L)
        val coordinator = RunInstanceCloseCoordinator(
            scope = this,
            state = { state },
            stopRun = { error("安装向导不应进入普通停止链") },
            cancelInstallWizard = { false },
        )

        val result = coordinator.close(command(state, generation = 10L))

        assertEquals(RunCommandResult.Ignored("install_wizard_close_rejected"), result)
    }

    @Test
    fun `旧代次与重复到达均不作用于新实例`() = runTest {
        val state = state(ownerKind = CardRunState.OWNER_KIND_CARD, generation = 12L)
        val stops = mutableListOf<RunStopCommand>()
        val coordinator = coordinator(this, state, stops = stops)

        val stale = coordinator.close(command(state, generation = 11L))
        val missing = RunInstanceCloseCoordinator(
            scope = this,
            state = { null },
            stopRun = { error("缺失实例不应停止") },
            cancelInstallWizard = { error("缺失实例不应取消") },
        ).close(command(state, generation = 12L))

        assertEquals(RunCommandResult.Ignored("generation_mismatch"), stale)
        assertEquals(RunCommandResult.Ignored("missing_instance"), missing)
        assertTrue(stops.isEmpty())
    }

    @Test
    fun `同一向导代次的重复关闭只执行一份取消清理`() = runTest {
        val state = state(ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD, generation = 15L)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cancellations = 0
        val coordinator = RunInstanceCloseCoordinator(
            scope = this,
            state = { state },
            stopRun = { error("安装向导不应进入普通停止链") },
            cancelInstallWizard = {
                cancellations += 1
                entered.complete(Unit)
                release.await()
                true
            },
        )

        val first = async { coordinator.close(command(state, generation = 15L)) }
        entered.await()
        val duplicate = coordinator.close(command(state, generation = 15L))
        release.complete(Unit)

        assertEquals(RunCommandResult.Ignored("close_in_progress"), duplicate)
        assertEquals(RunCommandResult.Accepted(state.instanceId), first.await())
        assertEquals(1, cancellations)
    }

    private fun coordinator(
        scope: CoroutineScope,
        state: CardRunState,
        stops: MutableList<RunStopCommand>,
        cancelled: MutableList<CardRunState> = mutableListOf(),
    ) = RunInstanceCloseCoordinator(
        scope = scope,
        state = { instanceId -> state.takeIf { it.instanceId == instanceId } },
        stopRun = { command ->
            stops += command
            RunCommandResult.Accepted(command.instanceId)
        },
        cancelInstallWizard = { wizard ->
            cancelled += wizard
            true
        },
    )

    private fun command(state: CardRunState, generation: Long) = RunInstanceCloseCommand(
        instanceId = state.instanceId,
        expectedGeneration = generation,
        source = RunInstanceCloseSource.TaskRemoved,
    )

    private fun state(ownerKind: String, generation: Long) = CardRunState(
        instanceId = "instance",
        recipeId = "recipe",
        ownerKind = ownerKind,
        status = CardRunStatus.Running,
        createdAt = generation,
        updatedAt = generation,
    )
}
