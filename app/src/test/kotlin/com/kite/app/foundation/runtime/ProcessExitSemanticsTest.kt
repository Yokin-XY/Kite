package com.kite.app.foundation.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessExitSemanticsTest {

    @Test
    fun `shell command unavailable exits are classified separately`() {
        assertTrue(ProcessExitSemantics.isCommandUnavailableExit(126))
        assertTrue(ProcessExitSemantics.isCommandUnavailableExit(127))
        assertFalse(ProcessExitSemantics.isCommandUnavailableExit(null))
        assertFalse(ProcessExitSemantics.isCommandUnavailableExit(0))
        assertFalse(ProcessExitSemantics.isCommandUnavailableExit(1))
        assertFalse(ProcessExitSemantics.isCommandUnavailableExit(137))
    }
}
