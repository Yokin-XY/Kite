package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProotTelemetryStoreLifecycleTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `同一 PID 的新遥测会话不会继承旧退出状态`() {
        val base = System.currentTimeMillis() - 10_000L
        val file = telemetryFile(
            event("TraceeCreated", base, "session-old", 100L, 51, 700, owner = "card:old"),
            event("TraceeExited", base + 100L, "session-old", 100L, 51, 700, exitCode = 0),
            event("TraceeCreated", base + 1_000L, "session-new", 200L, 52, 700, owner = "card:new")
        )

        val snapshot = ProotTelemetryStore.readTelemetryFileForTests(file)

        assertEquals(2, snapshot.tracees.size)
        assertEquals(2, snapshot.tracees.map { it.lifecycleId }.distinct().size)
        assertFalse(snapshot.tracees.single { it.telemetrySessionId == "session-old" }.running)
        assertTrue(snapshot.tracees.single { it.telemetrySessionId == "session-new" }.running)
        assertEquals(1, snapshot.liveTraceeCount)
        assertEquals(listOf("card:new"), snapshot.ownerProcessIndex.groups.map { it.ownerId })
    }

    @Test
    fun `同一会话的终态仍结束原生命周期`() {
        val base = System.currentTimeMillis() - 10_000L
        val file = telemetryFile(
            event("TraceeCreated", base, "session-a", 100L, 51, 701, owner = "card:a"),
            event("ExecDetected", base + 50L, "session-a", 100L, 51, 701, owner = "card:a"),
            event("TraceeSignaled", base + 100L, "session-a", 100L, 51, 701, signal = 15)
        )

        val snapshot = ProotTelemetryStore.readTelemetryFileForTests(file)

        assertEquals(1, snapshot.tracees.size)
        assertFalse(snapshot.tracees.single().running)
        assertEquals(15, snapshot.tracees.single().signal)
        assertEquals(0, snapshot.liveTraceeCount)
    }

    @Test
    fun `遥测 Store 生成工作负载索引且不按进程名称归组`() {
        val base = System.currentTimeMillis() - 10_000L
        val file = telemetryFile(
            event("TraceeCreated", base, "scope-session", 100L, 90, 100, eventSeq = 1L, lifecycleSeq = 1L, processGroupId = 100, sessionId = 100),
            event("ForkDetected", base + 10L, "scope-session", 100L, 90, 200, parentTraceePid = 100, eventSeq = 2L, lifecycleSeq = 2L, parentLifecycleSeq = 1L, processGroupId = 200, sessionId = 100),
            event("ForkDetected", base + 20L, "scope-session", 100L, 90, 201, parentTraceePid = 200, eventSeq = 3L, lifecycleSeq = 3L, parentLifecycleSeq = 2L, processGroupId = 200, sessionId = 100),
            event("ForkDetected", base + 30L, "scope-session", 100L, 90, 300, parentTraceePid = 100, eventSeq = 4L, lifecycleSeq = 4L, parentLifecycleSeq = 1L, processGroupId = 300, sessionId = 100),
        )

        val snapshot = ProotTelemetryStore.readTelemetryFileForTests(file)
        val entries = snapshot.processLiveTable.entries.associateBy(ProotLiveProcessEntry::traceePid)

        assertEquals(entries.getValue(200).workloadScopeId, entries.getValue(201).workloadScopeId)
        assertFalse(entries.getValue(200).workloadScopeId == entries.getValue(300).workloadScopeId)
        assertTrue(entries.getValue(100).isWorkloadLauncher)
        assertFalse(entries.getValue(200).isWorkloadLauncher)
        assertEquals(3, snapshot.workloadScopeIndex.scopeCount)
        assertEquals(4, snapshot.workloadScopeIndex.liveTraceeCount)
    }

    @Test
    fun `v2 生命周期编号允许同一会话复用 host PID 而不串代`() {
        val base = System.currentTimeMillis() - 10_000L
        val file = telemetryFile(
            event(
                "TraceeCreated",
                base,
                "session-v2",
                100L,
                51,
                701,
                owner = "card:old",
                eventSeq = 1L,
                lifecycleSeq = 10L,
                startTimeTicks = 1000L,
            ),
            event(
                "TraceeExited",
                base + 100L,
                "session-v2",
                100L,
                51,
                701,
                exitCode = 0,
                eventSeq = 2L,
                lifecycleSeq = 10L,
                startTimeTicks = 1000L,
            ),
            event(
                "TraceeCreated",
                base + 200L,
                "session-v2",
                100L,
                51,
                701,
                owner = "card:new",
                eventSeq = 3L,
                lifecycleSeq = 11L,
                startTimeTicks = 2000L,
            ),
        )

        val snapshot = ProotTelemetryStore.readTelemetryFileForTests(file)

        assertEquals(2, snapshot.tracees.size)
        assertEquals(2, snapshot.tracees.map(ProotTraceeRecord::lifecycleId).distinct().size)
        assertFalse(snapshot.tracees.single { it.lifecycleSeq == 10L }.running)
        assertTrue(snapshot.tracees.single { it.lifecycleSeq == 11L }.running)
        assertEquals(2000L, snapshot.tracees.single(ProotTraceeRecord::running).startTimeTicks)
    }

    @Test
    fun `v2 事件缺口降低 owner 证据而重复事件不会重复投影`() {
        val base = System.currentTimeMillis() - 10_000L
        val duplicate = event(
            "ExecDetected",
            base + 100L,
            "session-gap",
            100L,
            51,
            702,
            owner = "card:a",
            eventSeq = 2L,
            lifecycleSeq = 20L,
        )
        val file = telemetryFile(
            event(
                "TraceeCreated",
                base,
                "session-gap",
                100L,
                51,
                702,
                owner = "card:a",
                eventSeq = 1L,
                lifecycleSeq = 20L,
            ),
            duplicate,
            duplicate,
            event(
                "ExecDetected",
                base + 200L,
                "session-gap",
                100L,
                51,
                702,
                owner = "card:a",
                eventSeq = 4L,
                lifecycleSeq = 20L,
            ),
        )

        val snapshot = ProotTelemetryStore.readTelemetryFileForTests(file)

        assertEquals(1L, snapshot.counters.duplicateEvents)
        assertEquals(1L, snapshot.counters.sequenceGaps)
        assertEquals("event_sequence_gap", snapshot.ownerEvidenceCoverageReason)
        assertEquals(2, snapshot.tracees.single().execCount)
    }

    @Test
    fun `旧协议 PID 终止后新的创建事件开启兼容代次`() {
        val base = System.currentTimeMillis() - 10_000L
        val file = telemetryFile(
            event("TraceeCreated", base, "", 0L, 61, 702, owner = "terminal:old"),
            event("TraceeExited", base + 100L, "", 0L, 61, 702, exitCode = 0),
            event("TraceeCreated", base + 1_000L, "", 0L, 61, 702, owner = "terminal:new")
        )

        val snapshot = ProotTelemetryStore.readTelemetryFileForTests(file)

        assertEquals(2, snapshot.tracees.size)
        assertEquals(1, snapshot.tracees.count(ProotTraceeRecord::running))
        assertEquals("terminal:new", snapshot.tracees.single(ProotTraceeRecord::running).kfRuntimeId)
    }

    @Test
    fun `子进程只继承同一遥测生命周期的父 owner`() {
        val base = System.currentTimeMillis() - 10_000L
        val file = telemetryFile(
            event("TraceeCreated", base, "session-old", 100L, 71, 710, owner = "card:old"),
            event("TraceeExited", base + 100L, "session-old", 100L, 71, 710, exitCode = 0),
            event("TraceeCreated", base + 1_000L, "session-new", 200L, 72, 711, owner = "card:new"),
            event(
                "ForkDetected",
                base + 1_100L,
                "session-new",
                200L,
                72,
                712,
                parentTraceePid = 711
            ),
            event(
                "ForkDetected",
                base + 1_200L,
                "session-new",
                200L,
                72,
                713,
                parentTraceePid = 710
            )
        )

        val snapshot = ProotTelemetryStore.readTelemetryFileForTests(file)

        assertEquals("card:new", snapshot.tracees.single { it.traceePid == 712 }.kfRuntimeId)
        assertEquals("", snapshot.tracees.single { it.traceePid == 713 }.kfRuntimeId)
    }

    @Test
    fun `大历史文件裁剪后新实例仍拥有完整 owner 停止证据`() {
        val now = System.currentTimeMillis()
        val base = now - 100_000L
        val currentGeneration = now - 1_000L
        val currentOwner = "card:current@$currentGeneration"
        val lines = buildList {
            repeat(1_800) { index ->
                add(
                    event(
                        eventType = "ExecDetected",
                        timestampMs = base + index,
                        telemetrySessionId = "history-session",
                        prootStartMs = base,
                        prootPid = 51,
                        traceePid = 2_000 + index
                    )
                )
            }
            add(
                event(
                    eventType = "TraceeCreated",
                    timestampMs = currentGeneration + 100L,
                    telemetrySessionId = "current-session",
                    prootStartMs = currentGeneration + 100L,
                    prootPid = 81,
                    traceePid = 9_001,
                    owner = currentOwner
                )
            )
        }
        val file = temp.newFile("kf-proot-telemetry-large.jsonl").apply {
            writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }

        val snapshot = ProotTelemetryStore.readTelemetryFileForTests(file, readerEpochMs = base)

        assertTrue(file.length() > 256L * 1024L)
        assertTrue(snapshot.counters.skippedBytes > 0L)
        assertTrue(snapshot.ownerEvidenceCompleteFromMs > base)
        assertTrue(snapshot.ownerProcessIndex.groups.any { it.ownerId == currentOwner })
        assertTrue(
            ProotOwnerTerminationEvidence.readiness(
                snapshot,
                snapshot.refreshedAtMs,
                ownerId = currentOwner
            ).usable
        )
        assertFalse(
            ProotOwnerTerminationEvidence.readiness(
                snapshot,
                snapshot.refreshedAtMs,
                ownerId = "card:old@$base"
            ).usable
        )
    }

    private fun telemetryFile(vararg lines: String): File {
        return temp.newFile("kf-proot-telemetry.jsonl").apply {
            writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    private fun event(
        eventType: String,
        timestampMs: Long,
        telemetrySessionId: String,
        prootStartMs: Long,
        prootPid: Int,
        traceePid: Int,
        owner: String = "",
        exitCode: Int? = null,
        signal: Int? = null,
        parentTraceePid: Int? = null,
        eventSeq: Long = 0L,
        lifecycleSeq: Long = 0L,
        startTimeTicks: Long = 0L,
        parentLifecycleSeq: Long? = null,
        processGroupId: Int? = null,
        sessionId: Int? = null,
    ): String {
        val fields = mutableListOf(
            "\"schema\":\"kf_proot_lifecycle_event_v1\"",
            "\"eventType\":\"$eventType\"",
            "\"timestampMs\":$timestampMs",
            "\"telemetrySessionId\":\"$telemetrySessionId\"",
            "\"prootStartMs\":$prootStartMs",
            "\"prootPid\":$prootPid",
            "\"traceePid\":$traceePid",
            "\"traceeVpid\":$traceePid",
            "\"sourceHook\":\"test\"",
            "\"costLevel\":\"lifecycle_low\"",
            "\"kfRuntimeId\":\"$owner\""
        )
        exitCode?.let { fields += "\"exitCode\":$it" }
        signal?.let { fields += "\"signal\":$it" }
        parentTraceePid?.let { fields += "\"parentTraceePid\":$it" }
        if (eventSeq > 0L) fields += "\"eventSeq\":$eventSeq"
        if (lifecycleSeq > 0L) fields += "\"lifecycleSeq\":$lifecycleSeq"
        if (startTimeTicks > 0L) fields += "\"startTimeTicks\":$startTimeTicks"
        parentLifecycleSeq?.let { fields += "\"parentLifecycleSeq\":$it" }
        processGroupId?.let { fields += "\"processGroupId\":$it" }
        sessionId?.let { fields += "\"sessionId\":$it" }
        return fields.joinToString(prefix = "{", postfix = "}")
    }
}
