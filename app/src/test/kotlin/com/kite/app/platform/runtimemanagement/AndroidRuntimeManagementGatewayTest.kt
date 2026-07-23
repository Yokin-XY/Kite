package com.kite.app.platform.runtimemanagement

import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.foundation.runtime.TaskManagerAction
import com.kite.app.foundation.runtime.TaskManagerProcessItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRuntimeManagementGatewayTest {
    @Test
    fun `maps card owner root without granting direct pid action`() {
        val mapped = AndroidRuntimeManagementGateway.run {
            process(
                id = "root-CARD-card-1",
                ownerId = "card:card-1",
                ownerKindLabel = "卡片"
            ).toRuntimeManagedProcess()
        }

        assertEquals(RuntimeManagedOwnerKind.Card, mapped.ownerKind)
        assertTrue(mapped.isOwnerRoot)
        assertFalse(mapped.isRuntimeScaffold)
        assertFalse(mapped.canEndDirectly)
    }

    @Test
    fun `maps ordinary child as direct process action`() {
        val mapped = AndroidRuntimeManagementGateway.run {
            process(
                id = "process-52",
                ownerId = "card:card-1",
                ownerKindLabel = "卡片",
                actions = listOf(TaskManagerAction.END_PROCESS)
            ).toRuntimeManagedProcess()
        }

        assertEquals(RuntimeManagedOwnerKind.Card, mapped.ownerKind)
        assertFalse(mapped.isOwnerRoot)
        assertTrue(mapped.canEndDirectly)
    }

    @Test
    fun `maps system generated workload identity without deriving it from title`() {
        val mapped = AndroidRuntimeManagementGateway.run {
            process(
                id = "process-52",
                workloadScopeId = "workload:session-a:2",
            ).toRuntimeManagedProcess()
        }

        assertEquals("workload:session-a:2", mapped.workloadScopeId)
    }

    @Test
    fun `maps session launch scope as infrastructure without shell name matching`() {
        val mapped = AndroidRuntimeManagementGateway.run {
            process(
                id = "process-41",
                commandLine = "custom-launch-command",
                isWorkloadLauncher = true,
            ).toRuntimeManagedProcess()
        }

        assertTrue(mapped.isRuntimeScaffold)
    }

    @Test
    fun `maps system process by stable platform identity`() {
        val mapped = AndroidRuntimeManagementGateway.run {
            process(
                id = "process-10",
                commandLine = "/workspace/.kf/system/bin/supervisord"
            ).toRuntimeManagedProcess()
        }

        assertEquals(RuntimeManagedOwnerKind.System, mapped.ownerKind)
        assertEquals("容器守护进程", mapped.title)
        assertTrue(mapped.isRuntimeScaffold)
    }

    @Test
    fun `maps capacity root as scaffold by stable runtime identity rather than root id`() {
        val mapped = AndroidRuntimeManagementGateway.run {
            process(
                id = "ubuntu-process-session-a:6",
                pid = 101,
                ownerId = "background-space-main-proot-capacity-worker-2",
                ownerKindLabel = "后台运行项",
                runtimeUnitId = "background:proot-capacity-worker:worker-2",
                linkedRuntimeId = "background-space-main-proot-capacity-worker-2",
                runtimeRootPid = 101,
                commandLine = "/bin/bash -lc trap; /run/kf-proot-capacity/worker-2.pid",
            ).toRuntimeManagedProcess()
        }

        assertEquals("PRoot 容量工作器", mapped.title)
        assertTrue(mapped.isOwnerRoot)
        assertTrue(mapped.isRuntimeScaffold)
    }

    @Test
    fun `maps capacity worker child as background foundation through propagated identity`() {
        val runtimeId = "background-space-main-proot-capacity-worker-2"
        val mapped = AndroidRuntimeManagementGateway.run {
            process(
                id = "ubuntu-process-session-a:7",
                pid = 102,
                ownerId = runtimeId,
                ownerKindLabel = "后台运行项",
                runtimeUnitId = "background:proot-capacity-worker:$runtimeId",
                linkedRuntimeId = runtimeId,
                runtimeRootPid = 101,
                title = "sleep",
                commandLine = "sleep 3600",
            ).toRuntimeManagedProcess()
        }

        assertEquals(RuntimeManagedOwnerKind.BackgroundRuntime, mapped.ownerKind)
        assertEquals("sleep", mapped.title)
        assertTrue(mapped.isRuntimeScaffold)
    }

    private fun process(
        id: String,
        pid: Int = 41,
        title: String = "proc",
        ownerId: String? = null,
        ownerKindLabel: String? = null,
        commandLine: String = "proc",
        workloadScopeId: String? = null,
        isWorkloadLauncher: Boolean = false,
        runtimeUnitId: String? = null,
        linkedRuntimeId: String? = null,
        runtimeRootPid: Int? = null,
        actions: List<TaskManagerAction> = emptyList()
    ): TaskManagerProcessItem = TaskManagerProcessItem(
        id = id,
        pid = pid,
        parentPid = 0,
        title = title,
        subtitle = "",
        sourceLabel = "",
        stateLabel = "运行中",
        rawState = "R",
        command = title,
        commandLine = commandLine,
        linkedRuntimeId = linkedRuntimeId,
        runtimeOwnerId = ownerId,
        runtimeUnitId = runtimeUnitId,
        runtimeRootPid = runtimeRootPid,
        workloadScopeId = workloadScopeId,
        isWorkloadLauncher = isWorkloadLauncher,
        runtimeOwnerKindLabel = ownerKindLabel,
        availableActions = actions
    )
}
