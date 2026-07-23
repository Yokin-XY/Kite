package com.kite.app.foundation.runtime

import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.platform.runtimemanagement.AndroidRuntimeManagementGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskManagerBackgroundRuntimeProjectionTest {
    @Test
    fun `capacity worker descendants inherit background runtime identity from telemetry parent`() {
        val runtimeId = "background-space-main-proot-capacity-worker-2"
        val unitId = "background:proot-capacity-worker:$runtimeId"
        val root = process(
            pid = 101,
            lifecycleSeq = 1L,
            executable = "/usr/bin/bash",
            argvPreview = "/bin/bash -lc while true; do sleep 3600 & wait \$!; done",
            runtimeId = runtimeId,
            unitId = unitId,
        )
        val child = process(
            pid = 102,
            lifecycleSeq = 2L,
            parentPid = 101,
            parentLifecycleSeq = 1L,
            executable = "/usr/bin/sleep",
            argvPreview = "sleep 3600",
        )
        val items = TaskManagerStore.buildItemsForTesting(
            RuntimeHealthSnapshot(
                prootTelemetry = ProotTelemetrySnapshot(
                    processLiveTable = ProotProcessLiveTable(
                        liveTraceeCount = 2,
                        knownTraceeCount = 2,
                        entries = listOf(root, child),
                    )
                )
            )
        )

        val projectedChild = items.single { it.pid == 102 }
        assertEquals(runtimeId, projectedChild.runtimeOwnerId)
        assertEquals(unitId, projectedChild.runtimeUnitId)
        assertEquals("后台运行项", projectedChild.runtimeOwnerKindLabel)
        assertEquals(runtimeId, projectedChild.linkedRuntimeId)
        assertEquals(101, projectedChild.runtimeRootPid)
    }

    @Test
    fun `observed capacity root and telemetry child both project as runtime foundation`() {
        val runtimeId = "background-space-main-proot-capacity-worker-2"
        val unitId = "background:proot-capacity-worker:$runtimeId"
        val root = process(
            pid = 101,
            lifecycleSeq = 1L,
            executable = "/usr/bin/bash",
            argvPreview = "/bin/bash -lc while true; do sleep 3600 & wait \$!; done",
            runtimeId = runtimeId,
            unitId = unitId,
        )
        val child = process(
            pid = 102,
            lifecycleSeq = 2L,
            parentPid = 101,
            parentLifecycleSeq = 1L,
            executable = "/usr/bin/sleep",
            argvPreview = "sleep 3600",
            runtimeId = runtimeId,
            unitId = unitId,
        )
        val items = TaskManagerStore.buildItemsForTesting(
            RuntimeHealthSnapshot(
                roots = listOf(
                    RuntimeRootSnapshot(
                        ownerKind = RuntimeRootOwnerKind.BACKGROUND_RUNTIME,
                        ownerId = runtimeId,
                        title = "PRoot 容量工作器",
                        statusLabel = "运行中",
                        observedPid = 101,
                        processCount = 2,
                        commandLine = root.argvPreview,
                        runtimeKind = BackgroundRuntimeKind.PROOT_CAPACITY_WORKER,
                        reality = RuntimeRootReality.OBSERVED,
                        processUnitId = unitId,
                    ),
                    RuntimeRootSnapshot(
                        ownerKind = RuntimeRootOwnerKind.UNATTRIBUTED,
                        ownerId = null,
                        title = "sleep",
                        statusLabel = "运行中",
                        observedPid = 102,
                        commandLine = "sleep 3600",
                        reality = RuntimeRootReality.OBSERVED,
                    )
                ),
                prootTelemetry = ProotTelemetrySnapshot(
                    processLiveTable = ProotProcessLiveTable(
                        liveTraceeCount = 2,
                        knownTraceeCount = 2,
                        entries = listOf(root, child),
                    )
                )
            )
        )

        val managed = AndroidRuntimeManagementGateway.run {
            items.map { it.toRuntimeManagedProcess() }
        }
        assertEquals(listOf(101, 102), managed.map { it.pid }.sorted())
        assertEquals("PRoot 容量工作器", managed.single { it.pid == 101 }.title)
        assertEquals("sleep", managed.single { it.pid == 102 }.title)
        assertTrue(managed.single { it.pid == 101 }.isOwnerRoot)
        assertEquals(101, managed.single { it.pid == 102 }.ownerRootPid)
        assertTrue(managed.all { it.isRuntimeScaffold })
        assertTrue(managed.all { it.linkedRuntimeId == runtimeId })
        assertTrue(items.single { it.pid == 102 }.processRef?.hasStrongIdentity == true)
    }

    private fun process(
        pid: Int,
        lifecycleSeq: Long,
        parentPid: Int? = null,
        parentLifecycleSeq: Long? = null,
        executable: String,
        argvPreview: String,
        runtimeId: String = "",
        unitId: String = "",
    ): ProotLiveProcessEntry = ProotLiveProcessEntry(
        prootPid = 90,
        telemetrySessionId = "session-capacity",
        prootStartMs = 1_000L,
        traceePid = pid,
        traceeVpid = lifecycleSeq,
        processGroupId = 81,
        sessionId = 80,
        parentTraceePid = parentPid,
        parentTraceeVpid = parentLifecycleSeq,
        state = ProotLiveProcessState.RUNNING,
        createdAtMs = 1_000L,
        lastSeenAtMs = 2_000L,
        exitedAtMs = null,
        signaledAtMs = null,
        lastEventType = ProotTelemetryEventType.ExecDetected,
        lastSourceHook = "test",
        lastCostLevel = "lifecycle_low",
        executable = executable,
        argvPreview = argvPreview,
        kfRuntimeId = runtimeId,
        kfUnitId = unitId,
        workloadScopeId = "workload:session-capacity:1",
        execCount = 1,
        childEventCount = 0,
        lifecycleSeq = lifecycleSeq,
        startTimeTicks = 10_000L + lifecycleSeq,
        parentLifecycleSeq = parentLifecycleSeq,
    )
}
