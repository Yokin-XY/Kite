package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskManagerSnapshotGraceTest {
    @Test
    fun `短暂空快照保留上一帧并给出到期时间`() {
        var now = 1_000L
        val grace = TaskManagerSnapshotGrace(graceMs = 1_500L, nowMs = { now })
        val running = snapshot(processes = listOf(process(100)))

        assertEquals(running, grace.accept(running).snapshot)

        now = 1_200L
        val guarded = grace.accept(snapshot(processes = emptyList(), refreshedAt = 2_000L))

        assertEquals(listOf(100), guarded.snapshot.processes.map(TaskManagerProcessItem::pid))
        assertEquals(1_500L, guarded.emptyExpiryDelayMs)
        assertEquals(2_000L, guarded.snapshot.refreshedAt)
    }

    @Test
    fun `保护期结束后空进程事实必须替换旧列表`() {
        var now = 1_000L
        val grace = TaskManagerSnapshotGrace(graceMs = 1_500L, nowMs = { now })
        grace.accept(snapshot(processes = listOf(process(100))))
        grace.accept(snapshot(processes = emptyList(), refreshedAt = 2_000L))

        now = 2_500L
        val expired = grace.accept(snapshot(processes = emptyList(), refreshedAt = 3_000L))

        assertEquals(emptyList<TaskManagerProcessItem>(), expired.snapshot.processes)
        assertEquals(3_000L, expired.snapshot.refreshedAt)
        assertNull(expired.emptyExpiryDelayMs)
    }

    @Test
    fun `保护期间出现新进程会以新事实重新开始`() {
        var now = 1_000L
        val grace = TaskManagerSnapshotGrace(graceMs = 1_500L, nowMs = { now })
        grace.accept(snapshot(processes = listOf(process(100))))
        grace.accept(snapshot(processes = emptyList()))

        now = 1_300L
        val replacement = snapshot(processes = listOf(process(200)))
        assertEquals(replacement, grace.accept(replacement).snapshot)

        now = 1_400L
        val guarded = grace.accept(snapshot(processes = emptyList()))
        assertEquals(listOf(200), guarded.snapshot.processes.map(TaskManagerProcessItem::pid))
        assertEquals(1_500L, guarded.emptyExpiryDelayMs)
    }

    @Test
    fun `确认 owner 已停止会立即撤掉对应旧进程并保留其他进程`() {
        val grace = TaskManagerSnapshotGrace(graceMs = 1_500L)
        grace.accept(
            snapshot(
                processes = listOf(
                    process(100, ownerId = "terminal:closed"),
                    process(200, ownerId = "terminal:other")
                )
            )
        )

        val retained = grace.confirmOwnersStopped(setOf("terminal:closed"))

        assertEquals(listOf(200), retained?.processes?.map(TaskManagerProcessItem::pid))
        assertEquals(
            listOf(200),
            grace.accept(snapshot(processes = emptyList())).snapshot.processes.map(TaskManagerProcessItem::pid)
        )
    }

    @Test
    fun `确认最后一个 owner 已停止后空快照不再进入保护期`() {
        val grace = TaskManagerSnapshotGrace(graceMs = 1_500L)
        grace.accept(snapshot(processes = listOf(process(100, ownerId = "terminal:closed"))))

        val cleared = grace.confirmOwnersStopped(setOf("terminal:closed"))
        val next = grace.accept(snapshot(processes = emptyList(), refreshedAt = 2_000L))

        assertEquals(emptyList<TaskManagerProcessItem>(), cleared?.processes)
        assertEquals(emptyList<TaskManagerProcessItem>(), next.snapshot.processes)
        assertNull(next.emptyExpiryDelayMs)
    }

    private fun snapshot(
        processes: List<TaskManagerProcessItem>,
        refreshedAt: Long = 1_000L
    ): TaskManagerSnapshot = TaskManagerSnapshot(
        spaceId = "space-main",
        processes = processes,
        refreshedAt = refreshedAt
    )

    private fun process(
        pid: Int,
        ownerId: String? = null
    ): TaskManagerProcessItem = TaskManagerProcessItem(
        id = "process-$pid",
        pid = pid,
        parentPid = 1,
        title = "测试进程",
        subtitle = "",
        sourceLabel = "Ubuntu 进程",
        stateLabel = "运行中",
        rawState = "R",
        command = "test",
        commandLine = "test",
        runtimeOwnerId = ownerId
    )
}
