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
class ProotProcessEvidenceTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `stat parser handles command spaces and stable identity fields`() {
        val stat = LinuxProcStatParser.parse(procStat(pid = 712, command = "node worker (a)", state = 'S'))

        requireNotNull(stat)
        assertEquals(712, stat.pid)
        assertEquals("node worker (a)", stat.command)
        assertEquals(ProotKernelProcessState.SLEEPING, stat.state)
        assertEquals(701, stat.parentPid)
        assertEquals(700, stat.processGroupId)
        assertEquals(699, stat.sessionId)
        assertEquals(123_456L, stat.startTimeTicks)
    }

    @Test
    fun `verifier rejects reused pid before destructive action`() {
        writeStat(pid = 712, startTimeTicks = 999_999L)
        val verifier = ProotProcessVerifier(temp.root) { 88L }

        val result = verifier.verify(ref(pid = 712, startTimeTicks = 123_456L))

        assertEquals(ProotProcessVerificationStatus.PID_REUSED, result.status)
        assertFalse(result.identityMatched)
        assertTrue(result.terminal)
        assertEquals(999_999L, result.observedStartTimeTicks)
        assertEquals(88L, result.verifiedAtElapsedMs)
    }

    @Test
    fun `verifier exposes zombie without claiming it is missing`() {
        writeStat(pid = 713, startTimeTicks = 123_456L, state = 'Z')
        val verifier = ProotProcessVerifier(temp.root) { 99L }

        val result = verifier.verify(ref(pid = 713, startTimeTicks = 123_456L))

        assertEquals(ProotProcessVerificationStatus.MATCHED_ZOMBIE, result.status)
        assertEquals(ProotKernelProcessState.ZOMBIE, result.kernelState)
        assertTrue(result.identityMatched)
        assertFalse(result.terminal)
    }

    @Test
    fun `missing proc entry is confirmed terminal for the registered lifecycle`() {
        val result = ProotProcessVerifier(temp.root) { 100L }
            .verify(ref(pid = 714, startTimeTicks = 123_456L))

        assertEquals(ProotProcessVerificationStatus.MISSING, result.status)
        assertTrue(result.terminal)
    }

    @Test
    fun `event sequence gap requests active snapshot while duplicate does not advance`() {
        val tracker = ProotEventContinuityTracker()

        assertEquals(ProotEventContinuityStatus.FIRST, tracker.observe("session-a", 10L).status)
        assertEquals(ProotEventContinuityStatus.CONTIGUOUS, tracker.observe("session-a", 11L).status)
        assertEquals(
            ProotEventContinuityStatus.DUPLICATE_OR_OLD,
            tracker.observe("session-a", 11L).status,
        )
        val gap = tracker.observe("session-a", 14L)
        assertEquals(ProotEventContinuityStatus.GAP, gap.status)
        assertEquals(11L, gap.previousEventSeq)
        assertTrue(gap.requiresSnapshot)
        assertEquals(ProotEventContinuityStatus.FIRST, tracker.observe("session-b", 1L).status)
    }

    private fun ref(pid: Int, startTimeTicks: Long) = ProotProcessRef(
        telemetrySessionId = "session-a",
        prootStartMs = 1_000L,
        prootPid = 51,
        lifecycleSeq = pid.toLong(),
        hostPid = pid,
        guestPid = pid.toLong(),
        startTimeTicks = startTimeTicks,
    )

    private fun writeStat(
        pid: Int,
        startTimeTicks: Long,
        state: Char = 'S',
    ) {
        val processDir = File(temp.root, pid.toString()).apply { mkdirs() }
        File(processDir, "stat").writeText(
            procStat(pid = pid, state = state, startTimeTicks = startTimeTicks),
        )
    }

    private fun procStat(
        pid: Int,
        command: String = "node",
        state: Char = 'S',
        startTimeTicks: Long = 123_456L,
    ): String {
        val fieldsAfterState = mutableListOf(
            "701", // 4 ppid
            "700", // 5 pgrp
            "699", // 6 session
        )
        fieldsAfterState += List(15) { "0" } // fields 7..21
        fieldsAfterState += startTimeTicks.toString() // field 22
        fieldsAfterState += List(20) { "0" }
        return "$pid ($command) $state ${fieldsAfterState.joinToString(" ")}"
    }
}
