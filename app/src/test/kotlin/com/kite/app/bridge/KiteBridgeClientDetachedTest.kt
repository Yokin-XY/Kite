package com.kite.app.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteBridgeClientDetachedTest {
    @Test
    fun detachedTimeoutWithPidIsAccepted() {
        assertTrue(detachedStartAccepted(timedOut = true, exitCode = -1, pid = "10265"))
        assertFalse(detachedStartAccepted(timedOut = true, exitCode = -1, pid = null))
    }
}
