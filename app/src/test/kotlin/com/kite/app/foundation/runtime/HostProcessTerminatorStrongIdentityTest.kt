package com.kite.app.foundation.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostProcessTerminatorStrongIdentityTest {
    @Test
    fun `identity mismatch is treated as original exit without sending a signal`() = runBlocking {
        var checks = 0
        val outcome = HostProcessTerminator.terminateExactHostProcess(
            pid = 1,
            isExactProcess = {
                checks += 1
                false
            },
        )

        assertTrue(checks > 0)
        assertTrue(outcome.exited)
        assertFalse(outcome.sentHangup)
        assertFalse(outcome.sentTerminate)
        assertFalse(outcome.sentKill)
    }
}
