package com.kite.app.feature.runtimemanagement

import com.kite.app.application.runtimemanagement.RuntimeManagedCardIcon
import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagedTerminal
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeManagementProjectorTest {
    @Test
    fun `card scope promotes applications below runtime scaffold and keeps process tree`() {
        val run = runState(terminalSessionId = "terminal-1", rootPid = "41", status = CardRunStatus.WaitingTerminal)
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                runs = listOf(run),
                terminals = listOf(RuntimeManagedTerminal("terminal-1", "A 终端", "运行中", 3, isLive = true)),
                processes = listOf(
                    process("root", 41, title = "Kite 命令启动器", ownerId = run.runtimeRootOwnerId, ownerKind = RuntimeManagedOwnerKind.Card, isOwnerRoot = true, isRuntimeScaffold = true),
                    process("wechat", 52, parentPid = 41, title = "WeChat", ownerId = run.runtimeOwnerId, ownerKind = RuntimeManagedOwnerKind.Card, canEndDirectly = true),
                    process("wechat-render", 53, parentPid = 52, title = "WeChat Renderer", ownerId = run.runtimeOwnerId, ownerKind = RuntimeManagedOwnerKind.Card, canEndDirectly = true),
                    process("ime", 60, parentPid = 41, title = "WeChat Input", ownerId = run.runtimeOwnerId, ownerKind = RuntimeManagedOwnerKind.Card, canEndDirectly = true),
                ),
                cardIconsByRecipeId = mapOf("recipe-1" to RuntimeManagedCardIcon("image", "default", "icons/a.png")),
                observedProcessCount = 4,
            )
        )

        assertEquals(RuntimeManagementSummaryUiState(1, 1, 4), state.summary)
        val card = state.runs.single()
        assertEquals("icons/a.png", card.icon.source)
        assertEquals(listOf("WeChat", "WeChat Input", "运行基础"), card.processGroups.map { it.title })
        assertEquals(listOf(0, 1), card.processGroups.first().processes.map { it.depth })
        assertTrue(card.processGroups.first().processes.first().stopAction?.target is RuntimeManagementActionTarget.EndProcess)
        assertTrue(card.processGroups.last().processes.single().stopAction?.target is RuntimeManagementActionTarget.StopRun)
        assertTrue(card.surfaces.any { it.surface == CardRunSurface.Terminal })
    }

    @Test
    fun `all scope merges same application across cards and retains card labels`() {
        val a = runState(instanceId = "a", recipeId = "recipe-a", recipeName = "卡片 A", rootPid = "41", status = CardRunStatus.Running)
        val b = runState(instanceId = "b", recipeId = "recipe-b", recipeName = "卡片 B", rootPid = "81", status = CardRunStatus.Running)
        val state = RuntimeManagementProjector.project(RuntimeManagementSnapshot(
            runs = listOf(a, b),
            processes = listOf(
                process("a-app", 42, parentPid = 41, title = "bash", ownerId = a.runtimeOwnerId, ownerKind = RuntimeManagedOwnerKind.Card),
                process("b-app", 82, parentPid = 81, title = "bash", ownerId = b.runtimeOwnerId, ownerKind = RuntimeManagedOwnerKind.Card),
            ),
        ))

        assertEquals(2, state.runs.size)
        val all = state.allProcessGroups.single()
        assertEquals("bash", all.title)
        assertEquals(2, all.processCount)
        assertEquals(listOf("卡片 A", "卡片 B"), all.cardLabels)
        assertEquals(setOf("卡片 A", "卡片 B"), all.processes.mapNotNull { it.cardLabel }.toSet())
    }

    @Test
    fun `card scope merges peer roots with the same application identity`() {
        val run = runState(rootPid = "41", status = CardRunStatus.Running)
        val state = RuntimeManagementProjector.project(RuntimeManagementSnapshot(
            runs = listOf(run),
            processes = listOf(
                process("scaffold", 41, title = "Kite 命令启动器", ownerId = run.runtimeRootOwnerId, ownerKind = RuntimeManagedOwnerKind.Card, isRuntimeScaffold = true),
                process("worker-a", 52, parentPid = 41, title = "opencode", ownerId = run.runtimeOwnerId, ownerKind = RuntimeManagedOwnerKind.Card),
                process("worker-b", 53, parentPid = 41, title = "opencode", ownerId = run.runtimeOwnerId, ownerKind = RuntimeManagedOwnerKind.Card),
            ),
        ))

        val group = state.runs.single().processGroups.first { it.title == "opencode" }
        assertEquals(2, group.processCount)
        assertEquals(listOf(52, 53), group.processes.map { it.pid })
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
            nextActionUrl = "https://example.com",
        )
        val state = RuntimeManagementProjector.project(RuntimeManagementSnapshot(runs = listOf(root, child)))

        assertEquals(1, state.runs.size)
        assertTrue(state.runs.single().surfaces.any { it.key == "child-web:Web" })
    }

    @Test
    fun `pending process stop changes only action projection and never card fact`() {
        val run = runState(status = CardRunStatus.Running, rootPid = "41")
        val child = process("process-52", 52, parentPid = 41, ownerId = run.runtimeOwnerId, ownerKind = RuntimeManagedOwnerKind.Card, canEndDirectly = true)
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(runs = listOf(run), processes = listOf(child)),
            mutations = mapOf("process:process-52" to RuntimeManagementMutation("process:process-52", RuntimeManagementMutationPhase.AwaitingConfirmation)),
        )

        val projected = state.runs.single().processGroups.single().processes.single()
        assertEquals("结束中", projected.stopAction?.label)
        assertFalse(projected.stopAction?.enabled ?: true)
        assertEquals(CardRunStatus.Running, state.runs.single().status)
    }

    @Test
    fun `unassigned processes and live standalone terminal share tail scope and all scope`() {
        val state = RuntimeManagementProjector.project(RuntimeManagementSnapshot(
            terminals = listOf(RuntimeManagedTerminal("terminal-orphan", "独立终端", "运行中", 1, rootPid = 77, isLive = true)),
            processes = listOf(
                process("system", 10, title = "systemd", ownerKind = RuntimeManagedOwnerKind.System, isRuntimeScaffold = true),
                process("other", 11, title = "bash", ownerKind = RuntimeManagedOwnerKind.Unattributed, canEndDirectly = true),
            ),
        ))

        assertEquals(3, state.unassignedProcessGroups.size)
        assertEquals(3, state.allProcessGroups.size)
        val terminal = state.unassignedProcessGroups.single { it.key.contains("terminal-orphan") }.processes.single()
        assertEquals(77, terminal.pid)
        assertTrue(terminal.stopAction?.target is RuntimeManagementActionTarget.EndTerminal)
        val bash = state.unassignedProcessGroups.single { it.title == "bash" }.processes.single()
        assertTrue(bash.stopAction?.target is RuntimeManagementActionTarget.EndProcess)
    }

    @Test
    fun `stopping run exposes deterministic disabled stop action`() {
        val state = RuntimeManagementProjector.project(RuntimeManagementSnapshot(runs = listOf(runState(status = CardRunStatus.Stopping))))
        val action = state.runs.single().stopAction
        assertEquals("停止中", action?.label)
        assertFalse(action?.enabled ?: true)
    }

    @Test
    fun `旧失败绑定没有实际进程时不保留运行卡片`() {
        val failed = runState(rootPid = "41", status = CardRunStatus.Failed)

        val state = RuntimeManagementProjector.project(RuntimeManagementSnapshot(runs = listOf(failed)))

        assertTrue(state.runs.isEmpty())
        assertEquals(0, state.summary.runningCards)
    }

    @Test
    fun `非运行状态只要还有真实进程就保留在运行管理`() {
        val pending = runState(rootPid = "41", status = CardRunStatus.CleanupPending)
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                runs = listOf(pending),
                processes = listOf(
                    process(
                        "remaining",
                        52,
                        parentPid = 41,
                        ownerId = pending.runtimeOwnerId,
                        ownerKind = RuntimeManagedOwnerKind.Card,
                    ),
                ),
            ),
        )

        assertEquals(1, state.runs.size)
        assertEquals(1, state.runs.single().processCount)
        assertEquals("停止待确认", state.runs.single().statusLabel)
    }

    @Test
    fun `verified application group exposes tree stop while each child keeps single stop`() {
        val run = runState(rootPid = "41", status = CardRunStatus.Running)
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                runs = listOf(run),
                processes = listOf(
                    process(
                        id = "life-parent",
                        pid = 52,
                        title = "wechat",
                        ownerId = run.runtimeOwnerId,
                        ownerKind = RuntimeManagedOwnerKind.Card,
                        canEndDirectly = true,
                        identityVerified = true,
                    ),
                    process(
                        id = "life-child",
                        pid = 53,
                        parentPid = 52,
                        title = "wechat-render",
                        ownerId = run.runtimeOwnerId,
                        ownerKind = RuntimeManagedOwnerKind.Card,
                        canEndDirectly = true,
                        identityVerified = true,
                    ),
                ),
            ),
        )

        val group = state.runs.single().processGroups.single()
        val target = group.stopAction?.target as RuntimeManagementActionTarget.EndProcessTree
        assertEquals(listOf("life-child", "life-parent"), target.processIds.sorted())
        assertTrue(group.processes.all { it.stopAction?.target is RuntimeManagementActionTarget.EndProcess })
    }

    @Test
    fun `child sharing the card root keeps single process stop`() {
        val run = runState(rootPid = "41", status = CardRunStatus.Running)
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                runs = listOf(run),
                processes = listOf(
                    process(
                        id = "card-root",
                        pid = 41,
                        title = "launcher",
                        ownerId = run.runtimeOwnerId,
                        ownerKind = RuntimeManagedOwnerKind.Card,
                        isOwnerRoot = true,
                        canEndDirectly = true,
                        identityVerified = true,
                    ),
                    process(
                        id = "child",
                        pid = 52,
                        parentPid = 41,
                        ownerRootPid = 41,
                        title = "sleep",
                        ownerId = run.runtimeOwnerId,
                        ownerKind = RuntimeManagedOwnerKind.Card,
                        canEndDirectly = true,
                        identityVerified = true,
                    ),
                ),
            ),
        )

        val processes = state.runs.single().processGroups.single().processes
        assertTrue(processes.single { it.pid == 41 }.stopAction?.target is RuntimeManagementActionTarget.StopRun)
        assertTrue(processes.single { it.pid == 52 }.stopAction?.target is RuntimeManagementActionTarget.EndProcess)
    }

    private fun runState(
        instanceId: String = "card-1",
        recipeId: String = "recipe-1",
        recipeName: String = "OpenClaw",
        parentInstanceId: String? = null,
        terminalSessionId: String? = null,
        rootPid: String? = null,
        status: CardRunStatus,
        surface: CardRunSurface = CardRunSurface.Summary,
        nextActionUrl: String? = null,
    ): CardRunState = CardRunState(
        runtimeRootOwnerId = "card:$instanceId@10",
        runtimeOwnerId = "card:$instanceId@10/step/0-start/attempt/1",
        runtimeUnitId = "card:$instanceId@10/step/0-start/attempt/1",
        ownedRuntimeOwnerIds = listOf("card:$instanceId@10", "card:$instanceId@10/step/0-start/attempt/1"),
        instanceId = instanceId,
        recipeId = recipeId,
        recipeName = recipeName,
        parentInstanceId = parentInstanceId,
        status = status,
        surface = surface,
        terminalSessionId = terminalSessionId,
        rootPid = rootPid,
        nextActionUrl = nextActionUrl,
        createdAt = 10L,
        updatedAt = 20L,
    )

    private fun process(
        id: String,
        pid: Int,
        parentPid: Int = 0,
        ownerRootPid: Int = 0,
        title: String = "proc-$pid",
        ownerId: String? = null,
        ownerKind: RuntimeManagedOwnerKind = RuntimeManagedOwnerKind.Unattributed,
        isOwnerRoot: Boolean = false,
        isRuntimeScaffold: Boolean = false,
        canEndDirectly: Boolean = false,
        identityVerified: Boolean = false,
    ): RuntimeManagedProcess = RuntimeManagedProcess(
        id = id,
        pid = pid,
        parentPid = parentPid,
        ownerRootPid = ownerRootPid,
        title = title,
        stateLabel = "运行中",
        ownerKind = ownerKind,
        ownerId = ownerId,
        isOwnerRoot = isOwnerRoot,
        isRuntimeScaffold = isRuntimeScaffold,
        canEndDirectly = canEndDirectly,
        lifecycleId = id.takeIf { identityVerified },
        identityVerified = identityVerified,
    )
}
