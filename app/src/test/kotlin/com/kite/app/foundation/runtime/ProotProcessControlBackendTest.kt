package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotProcessControlBackendTest {
    @Test
    fun `refuses reused pid before sending signal`() {
        val sent = mutableListOf<Int>()
        val backend = backend(
            statuses = mapOf(20 to ProotProcessVerificationStatus.PID_REUSED),
            sent = sent,
        )

        val result = backend.signal(listOf(target(20)), ProotControlSignal.KILL)

        assertTrue(sent.isEmpty())
        assertEquals(listOf(20), result.refusedHostPids)
    }

    @Test
    fun `signals children before their parent`() {
        val sent = mutableListOf<Int>()
        val backend = backend(sent = sent)

        backend.signal(
            listOf(
                target(100),
                target(101, parentPid = 100),
                target(102, parentPid = 101),
            ),
            ProotControlSignal.TERM,
        )

        assertEquals(listOf(102, 101, 100), sent)
    }

    @Test
    fun `zombie is terminal and is not signalled`() {
        val sent = mutableListOf<Int>()
        val backend = backend(
            statuses = mapOf(30 to ProotProcessVerificationStatus.MATCHED_ZOMBIE),
            sent = sent,
        )

        val result = backend.signal(listOf(target(30)), ProotControlSignal.TERM)

        assertTrue(sent.isEmpty())
        assertEquals("identity_matched_zombie", result.attempts.single().reason)
    }

    private fun backend(
        statuses: Map<Int, ProotProcessVerificationStatus> = emptyMap(),
        sent: MutableList<Int>,
    ): ProotProcessControlBackend = ProotProcessControlBackend(
        verifier = ProotProcessIdentityVerifier { ref ->
            val status = statuses[ref.hostPid] ?: ProotProcessVerificationStatus.MATCHED_ACTIVE
            ProotProcessVerification(
                ref = ref,
                status = status,
                verifiedAtElapsedMs = 1L,
                reason = "test",
            )
        },
        signalSender = ProotProcessSignalSender { pid, _ -> sent += pid; true },
    )

    private fun target(pid: Int, parentPid: Int? = null): ProotProcessControlTarget =
        ProotProcessControlTarget(
            ref = ProotProcessRef(
                telemetrySessionId = "s",
                prootStartMs = 1L,
                prootPid = 10,
                lifecycleSeq = pid.toLong(),
                hostPid = pid,
                guestPid = pid.toLong(),
                startTimeTicks = pid * 10L,
            ),
            parentHostPid = parentPid,
        )
}
