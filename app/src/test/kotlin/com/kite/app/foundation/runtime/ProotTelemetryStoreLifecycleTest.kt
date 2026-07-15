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
        parentTraceePid: Int? = null
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
        return fields.joinToString(prefix = "{", postfix = "}")
    }
}
