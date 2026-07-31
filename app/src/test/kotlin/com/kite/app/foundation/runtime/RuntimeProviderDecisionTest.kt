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

    @Test
    fun `provider interface keeps preparation context separate from execution request`() {
        val provider = object : RuntimeExecutionProvider<String, List<String>> {
            override val kind = RuntimeProviderKind.MANAGED_RUNTIME

            override fun prepare(
                context: String,
                request: RuntimeExecutionRequest,
            ): RuntimeProviderDecision<List<String>> {
                val payload = request.payload as RuntimeExecutionPayload.Argv
                return RuntimeProviderDecision.Ready(
                    provider = kind,
                    plan = listOf(context, payload.executable) + payload.arguments,
                    reason = "test_ready",
                )
            }
        }

        val decision = provider.prepare(
            context = "provider-context",
            request = RuntimeExecutionRequest(RuntimeExecutionPayload.Argv("node", listOf("--version"))),
        ) as RuntimeProviderDecision.Ready

        assertEquals(listOf("provider-context", "node", "--version"), decision.plan)
    }
}
