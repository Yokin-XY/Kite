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
class ProotActiveRegistryReaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `stable session exposes only active tracee files`() {
        val session = sessionDir("session-a")
        writeMeta(session, eventSeq = 8L)
        writeEntry(session, lifecycleSeq = 10L, eventSeq = 7L, pid = 701)
        writeEntry(session, lifecycleSeq = 11L, eventSeq = 8L, pid = 702)

        val snapshot = ProotActiveRegistryReader(temp.root).read()

        assertEquals(ProotActiveRegistryReadStatus.LOADED, snapshot.status)
        assertTrue(snapshot.complete)
        assertEquals(2, snapshot.activeTraceeCount)
        assertEquals(listOf(10L, 11L), snapshot.sessions.single().entries.map { it.lifecycleSeq })
        assertEquals(8L, snapshot.sessions.single().lastEventSeq)
    }

    @Test
    fun `updating marker rejects half written session`() {
        val session = sessionDir("session-a")
        writeMeta(session, eventSeq = 8L)
        writeEntry(session, lifecycleSeq = 10L, eventSeq = 8L, pid = 701)
        File(session, ".updating").writeText("9\n")

        val snapshot = ProotActiveRegistryReader(temp.root, maxSessionReadAttempts = 1).read()

        assertEquals(ProotActiveRegistryReadStatus.UNSTABLE, snapshot.status)
        assertFalse(snapshot.complete)
        assertEquals(listOf("session-a"), snapshot.unstableSessionIds)
    }

    @Test
    fun `entry newer than meta is rejected instead of accepted partially`() {
        val session = sessionDir("session-a")
        writeMeta(session, eventSeq = 8L)
        writeEntry(session, lifecycleSeq = 10L, eventSeq = 9L, pid = 701)

        val snapshot = ProotActiveRegistryReader(temp.root, maxSessionReadAttempts = 1).read()

        assertEquals(ProotActiveRegistryReadStatus.UNSTABLE, snapshot.status)
        assertEquals(0, snapshot.activeTraceeCount)
    }

    @Test
    fun `targeted read is not blocked by an unrelated updating session`() {
        val target = sessionDir("target-session")
        writeMeta(target, eventSeq = 8L)
        writeEntry(target, lifecycleSeq = 10L, eventSeq = 8L, pid = 701)
        val unrelated = sessionDir("unrelated-session")
        writeMeta(unrelated, eventSeq = 5L)
        File(unrelated, ".updating").writeText("6\n")

        val snapshot = ProotActiveRegistryReader(temp.root, maxSessionReadAttempts = 1)
            .readSessions(listOf("target-session"))

        assertEquals(ProotActiveRegistryReadStatus.LOADED, snapshot.status)
        assertTrue(snapshot.complete)
        assertEquals(listOf("target-session"), snapshot.sessions.map { it.telemetrySessionId })
        assertEquals(listOf(701), snapshot.sessions.single().entries.map { it.traceePid })
    }

    @Test
    fun `targeted read treats a retired session directory as stable silence`() {
        val snapshot = ProotActiveRegistryReader(temp.root)
            .readSessions(listOf("already-retired"))

        assertEquals(ProotActiveRegistryReadStatus.LOADED, snapshot.status)
        assertTrue(snapshot.complete)
        assertTrue(snapshot.sessions.isEmpty())
    }

    @Test
    fun `full read ignores hidden retirement staging directories`() {
        val retired = sessionDir(".retired-session-a-9")
        File(retired, ".updating").writeText("9\n")

        val snapshot = ProotActiveRegistryReader(temp.root, maxSessionReadAttempts = 1).read()

        assertEquals(ProotActiveRegistryReadStatus.LOADED, snapshot.status)
        assertTrue(snapshot.complete)
        assertTrue(snapshot.unstableSessionIds.isEmpty())
    }

    private fun sessionDir(name: String): File = File(temp.root, name).apply { mkdirs() }

    private fun writeMeta(session: File, eventSeq: Long) {
        File(session, "meta.json").writeText(
            """{"schema":"kf_proot_active_registry_v1","telemetrySessionId":"${session.name}","prootStartMs":1000,"prootPid":51,"lastEventSeq":$eventSeq,"stable":true}""",
        )
    }

    private fun writeEntry(session: File, lifecycleSeq: Long, eventSeq: Long, pid: Int) {
        File(session, "$lifecycleSeq.json").writeText(
            """{"schema":"kf_proot_active_tracee_v1","telemetrySessionId":"${session.name}","prootStartMs":1000,"prootPid":51,"lastEventSeq":$eventSeq,"lifecycleSeq":$lifecycleSeq,"traceePid":$pid,"traceeVpid":$lifecycleSeq,"startTimeTicks":123456,"processGroupId":700,"sessionId":699,"parentTraceePid":null,"parentTraceeVpid":null,"parentLifecycleSeq":null,"lastEventType":"ExecDetected","executable":"/usr/bin/node","argvHash":"hash","argvPreview":"node worker","cwd":"/workspace","kfRuntimeId":"card:a","kfUnitId":"unit:a"}""",
        )
    }
}
