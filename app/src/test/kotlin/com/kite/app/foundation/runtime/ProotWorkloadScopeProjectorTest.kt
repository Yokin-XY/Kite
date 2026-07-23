package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProotWorkloadScopeProjectorTest {
    @Test
    fun `同一 Linux 作业的异名父子与 pipeline 兄弟共享作用域`() {
        val root = process(session = "session-a", lifecycle = 1, pid = 100, pgid = 100, sid = 100)
        val app = process(session = "session-a", lifecycle = 2, pid = 200, parent = 1, pgid = 200, sid = 100)
        val renderer = process(session = "session-a", lifecycle = 3, pid = 201, parent = 2, pgid = 200, sid = 100)
        val pipelinePeer = process(session = "session-a", lifecycle = 4, pid = 202, parent = 1, pgid = 200, sid = 100)

        val scopes = ProotWorkloadScopeProjector.project(listOf(root, app, renderer, pipelinePeer))

        assertEquals(scopes.getValue(app.lifecycleId), scopes.getValue(renderer.lifecycleId))
        assertEquals(scopes.getValue(app.lifecycleId), scopes.getValue(pipelinePeer.lifecycleId))
        assertNotEquals(scopes.getValue(root.lifecycleId), scopes.getValue(app.lifecycleId))
    }

    @Test
    fun `同一 PRoot 会话内不同作业保持独立`() {
        val root = process(session = "session-a", lifecycle = 1, pid = 100, pgid = 100, sid = 100)
        val first = process(session = "session-a", lifecycle = 2, pid = 200, parent = 1, pgid = 200, sid = 100)
        val second = process(session = "session-a", lifecycle = 3, pid = 300, parent = 1, pgid = 300, sid = 100)

        val scopes = ProotWorkloadScopeProjector.project(listOf(root, first, second))

        assertNotEquals(scopes.getValue(first.lifecycleId), scopes.getValue(second.lifecycleId))
    }

    @Test
    fun `不同 PRoot 会话不会因相同 PGID 合并`() {
        val aRoot = process(session = "session-a", lifecycle = 1, pid = 100, pgid = 100, sid = 100)
        val aJob = process(session = "session-a", lifecycle = 2, pid = 200, parent = 1, pgid = 200, sid = 100)
        val bRoot = process(session = "session-b", lifecycle = 1, pid = 100, pgid = 100, sid = 100)
        val bJob = process(session = "session-b", lifecycle = 2, pid = 200, parent = 1, pgid = 200, sid = 100)

        val scopes = ProotWorkloadScopeProjector.project(listOf(aRoot, aJob, bRoot, bJob))

        assertNotEquals(scopes.getValue(aJob.lifecycleId), scopes.getValue(bJob.lifecycleId))
    }

    @Test
    fun `没有内核作业变化时整棵派生树继承会话根作用域`() {
        val root = process(session = "session-a", lifecycle = 1, pid = 100, pgid = 100, sid = null)
        val child = process(session = "session-a", lifecycle = 2, pid = 101, parent = 1, pgid = 100, sid = null)
        val grandchild = process(session = "session-a", lifecycle = 3, pid = 102, parent = 2, pgid = 100, sid = null)

        val scopes = ProotWorkloadScopeProjector.project(listOf(root, child, grandchild))

        assertEquals(scopes.getValue(root.lifecycleId), scopes.getValue(child.lifecycleId))
        assertEquals(scopes.getValue(root.lifecycleId), scopes.getValue(grandchild.lifecycleId))
    }

    @Test
    fun `目标会话活动注册表使用同一作用域协议重新投影`() {
        val records = listOf(
            process(session = "session-a", lifecycle = 1, pid = 100, pgid = 100, sid = 100),
            process(session = "session-a", lifecycle = 2, pid = 200, parent = 1, pgid = 200, sid = 100),
            process(session = "session-a", lifecycle = 3, pid = 201, parent = 2, pgid = 200, sid = 100),
        )
        val registryEntries = records.map(::activeProcess)

        assertEquals(
            ProotWorkloadScopeProjector.project(records),
            ProotWorkloadScopeProjector.projectRegistry(registryEntries),
        )
    }

    @Test
    fun `组长退出后活动子进程仍沿历史父链继承原作用域`() {
        val root = process(session = "session-a", lifecycle = 1, pid = 100, pgid = 100, sid = 100)
        val leader = process(session = "session-a", lifecycle = 2, pid = 200, parent = 1, pgid = 200, sid = 100)
        val survivor = process(session = "session-a", lifecycle = 3, pid = 201, parent = 2, pgid = 200, sid = 100)
        val history = listOf(root, leader, survivor)

        val projected = ProotWorkloadScopeProjector.projectRegistry(
            entries = listOf(activeProcess(survivor)),
            historicalRecords = history,
        )

        assertEquals(
            ProotWorkloadScopeProjector.project(history).getValue(survivor.lifecycleId),
            projected.getValue(survivor.lifecycleId),
        )
    }

    private fun process(
        session: String,
        lifecycle: Long,
        pid: Int,
        parent: Long? = null,
        pgid: Int?,
        sid: Int?,
    ): ProotTraceeRecord = ProotTraceeRecord(
        traceePid = pid,
        traceeVpid = lifecycle,
        telemetrySessionId = session,
        prootStartMs = 1000L,
        prootPid = 90,
        processGroupId = pgid,
        sessionId = sid,
        parentTraceePid = null,
        parentTraceeVpid = parent,
        createdAtMs = 1000L + lifecycle,
        lastEventAtMs = 1000L + lifecycle,
        lastEventType = ProotTelemetryEventType.ExecDetected,
        eventSeq = lifecycle,
        lifecycleSeq = lifecycle,
        startTimeTicks = 10_000L + lifecycle,
        parentLifecycleSeq = parent,
    )

    private fun activeProcess(record: ProotTraceeRecord): ProotActiveTraceeEntry =
        ProotActiveTraceeEntry(
            telemetrySessionId = record.telemetrySessionId,
            prootStartMs = record.prootStartMs,
            prootPid = record.prootPid,
            lastEventSeq = record.eventSeq,
            lifecycleSeq = record.lifecycleSeq,
            traceePid = record.traceePid,
            traceeVpid = record.traceeVpid,
            startTimeTicks = record.startTimeTicks,
            processGroupId = record.processGroupId,
            sessionId = record.sessionId,
            parentTraceePid = record.parentTraceePid,
            parentTraceeVpid = record.parentTraceeVpid,
            parentLifecycleSeq = record.parentLifecycleSeq,
            lastEventType = record.lastEventType,
            executable = record.executable,
            argvHash = record.argvHash,
            argvPreview = record.argvPreview,
            cwd = record.cwd,
            kfRuntimeId = record.kfRuntimeId,
            kfUnitId = record.kfUnitId,
        )
}
