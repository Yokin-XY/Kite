package com.kite.app.feature.runtimemanagement

import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagedTerminal
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeManagementProjectorTest {
    @Test
    fun `projects card terminal root and child process into one traceable group`() {
        val run = runState(
            terminalSessionId = "terminal-1",
            rootPid = "41",
            status = CardRunStatus.WaitingTerminal,
            surface = CardRunSurface.Terminal
        )
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                runs = listOf(run),
                terminals = listOf(
                    RuntimeManagedTerminal(
                        id = "terminal-1",
                        title = "OpenClaw 终端",
                        statusLabel = "运行中",
                        processCount = 2,
                        isLive = true
                    )
                ),
                processes = listOf(
                    process(
                        id = "root-card-card-1",
                        pid = 41,
                        ownerId = "card:card-1",
                        ownerKind = RuntimeManagedOwnerKind.Card,
                        isOwnerRoot = true
                    ),
                    process(
                        id = "process-52",
                        pid = 52,
                        parentPid = 41,
                        ownerId = "card:card-1",
                        ownerKind = RuntimeManagedOwnerKind.Card,
                        canEndDirectly = true
                    )
                ),
                observedProcessCount = 2,
                refreshedAt = 99L
            )
        )

        assertEquals(RuntimeManagementSummaryUiState(1, 1, 2), state.summary)
        assertEquals(1, state.runs.size)
        val group = state.runs.single()
        assertEquals("card-1", group.instanceId)
        assertEquals("OpenClaw 终端", group.terminalTitle)
        assertEquals(2, group.processCount)
        assertEquals(41, group.mainProcess?.pid)
        assertEquals(listOf(52), group.childProcesses.map { it.pid })
        assertTrue(group.mainProcess?.stopAction?.target is RuntimeManagementActionTarget.StopRun)
        assertTrue(group.childProcesses.single().stopAction?.target is RuntimeManagementActionTarget.EndProcess)
        assertTrue(group.surfaces.any { it.surface == CardRunSurface.Terminal })
        assertTrue(state.otherProcessSections.isEmpty())
    }

    @Test
    fun `child run surfaces fold into root instead of creating duplicate cards`() {
        val root = runState(status = CardRunStatus.Running, surface = CardRunSurface.Report)
        val child = runState(
            instanceId = "child-web",
            recipeId = "child-recipe",
            parentInstanceId = root.instanceId,
            status = CardRunStatus.Opened,
            surface = CardRunSurface.Web,
            nextActionUrl = "https://example.com"
        )

        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(runs = listOf(root, child))
        )

        assertEquals(1, state.runs.size)
        assertTrue(state.runs.single().surfaces.any { it.key == "child-web:Web" })
    }

    @Test
    fun `pending process stop changes only action projection and never card fact`() {
        val run = runState(status = CardRunStatus.Running, rootPid = "41")
        val process = process(
            id = "process-52",
            pid = 52,
            parentPid = 41,
            ownerId = "card:card-1",
            ownerKind = RuntimeManagedOwnerKind.Card,
            canEndDirectly = true
        )
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(runs = listOf(run), processes = listOf(process)),
            mutations = mapOf(
                "process:process-52" to RuntimeManagementMutation(
                    key = "process:process-52",
                    phase = RuntimeManagementMutationPhase.AwaitingConfirmation
                )
            )
        )

        val child = state.runs.single().childProcesses.single()
        assertEquals("结束中", child.stopAction?.label)
        assertFalse(child.stopAction?.enabled ?: true)
        assertEquals(CardRunStatus.Running, state.runs.single().status)
    }

    @Test
    fun `stopping run has deterministic disabled stop action`() {
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(runs = listOf(runState(status = CardRunStatus.Stopping)))
        )

        val action = state.runs.single().stopAction
        assertEquals("停止中", action?.label)
        assertFalse(action?.enabled ?: true)
        assertEquals("停止中", state.runs.single().statusLabel)
    }

    @Test
    fun `system and unattributed processes stay outside card groups`() {
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                processes = listOf(
                    process("system-1", 10, ownerKind = RuntimeManagedOwnerKind.System),
                    process("other-1", 11, ownerKind = RuntimeManagedOwnerKind.Unattributed, canEndDirectly = true)
                )
            )
        )

        assertTrue(state.runs.isEmpty())
        assertEquals(listOf("系统", "其他"), state.otherProcessSections.map { it.title })
        assertNull(state.otherProcessSections.first().processes.single().stopAction)
        assertTrue(
            state.otherProcessSections.last().processes.single().stopAction?.target is
                RuntimeManagementActionTarget.EndProcess
        )
    }

    private fun runState(
        instanceId: String = "card-1",
        recipeId: String = "recipe-1",
        parentInstanceId: String? = null,
        terminalSessionId: String? = null,
        rootPid: String? = null,
        status: CardRunStatus,
        surface: CardRunSurface = CardRunSurface.Summary,
        nextActionUrl: String? = null
    ): CardRunState = CardRunState(
        instanceId = instanceId,
        recipeId = recipeId,
        recipeName = "OpenClaw",
        parentInstanceId = parentInstanceId,
        status = status,
        surface = surface,
        terminalSessionId = terminalSessionId,
        rootPid = rootPid,
        nextActionUrl = nextActionUrl,
        createdAt = 10L,
        updatedAt = 20L
    )

    private fun process(
        id: String,
        pid: Int,
        parentPid: Int = 0,
        ownerId: String? = null,
        ownerKind: RuntimeManagedOwnerKind = RuntimeManagedOwnerKind.Unattributed,
        isOwnerRoot: Boolean = false,
        canEndDirectly: Boolean = false
    ): RuntimeManagedProcess = RuntimeManagedProcess(
        id = id,
        pid = pid,
        parentPid = parentPid,
        title = "proc-$pid",
        stateLabel = "运行中",
        ownerKind = ownerKind,
        ownerId = ownerId,
        isOwnerRoot = isOwnerRoot,
        canEndDirectly = canEndDirectly
    )
}
