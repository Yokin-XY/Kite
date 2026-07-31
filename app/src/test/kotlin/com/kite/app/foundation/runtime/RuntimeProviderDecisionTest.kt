package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProviderDecisionTest {
    @Test
    fun `ready unsupported and blocked keep provider evidence distinct`() {
        val ready = RuntimeProviderDecision.Ready(
            RuntimeProviderKind.MANAGED_RUNTIME,
            plan = listOf("node", "--version"),
            reason = "host_node_ready",
        )
        val unsupported = RuntimeProviderDecision.Unsupported(
            RuntimeProviderKind.MANAGED_RUNTIME,
            "full_linux_required",
        )
        val blocked = RuntimeProviderDecision.Blocked(
            RuntimeProviderKind.MANAGED_RUNTIME,
            "runtime_identity_invalid",
        )

        assertEquals(RuntimeProviderKind.MANAGED_RUNTIME, ready.provider)
        assertEquals(listOf("node", "--version"), ready.plan)
        assertEquals("full_linux_required", unsupported.reason)
        assertEquals("runtime_identity_invalid", blocked.reason)
    }

    @Test
    fun `fallback policy only permits provider change before start`() {
        assertTrue(RuntimeFallbackPolicy.BEFORE_START_ONLY.allowsProviderFallback())
        assertFalse(RuntimeFallbackPolicy.DISABLED.allowsProviderFallback())
    }
}

