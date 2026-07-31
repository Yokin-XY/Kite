package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class RuntimeExecutionRequestTest {
    @Test
    fun `structured argv keeps provider facts separate from shell text`() {
        val request = RuntimeExecutionRequest(
            payload = RuntimeExecutionPayload.Argv("node", listOf("gateway", "--port", "3000")),
            workingDirectory = "/workspace",
            environment = mapOf("KITE_RUN_ID" to "run-1"),
            requirements = setOf(RuntimeExecutionRequirement.INTERACTIVE_PTY),
        )

        val payload = request.payload as RuntimeExecutionPayload.Argv
        assertEquals("node", payload.executable)
        assertEquals(listOf("gateway", "--port", "3000"), payload.arguments)
        assertEquals(RuntimeFallbackPolicy.BEFORE_START_ONLY, request.fallbackPolicy)
        assertTrue(RuntimeExecutionRequirement.INTERACTIVE_PTY in request.requirements)
    }

    @Test
    fun `native capability cannot masquerade as executable`() {
        val request = RuntimeExecutionRequest(
            payload = RuntimeExecutionPayload.NativeCapability(
                capabilityId = "network.download_sha256",
                parameters = mapOf("URL" to "https://example.invalid/archive.zip"),
            ),
            requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
        )

        val payload = request.payload as RuntimeExecutionPayload.NativeCapability
        assertEquals("network.download_sha256", payload.capabilityId)
        assertTrue(RuntimeExecutionRequirement.ANDROID_NATIVE in request.requirements)
    }

    @Test
    fun `invalid environment and nul arguments fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv("node"),
                environment = mapOf("INVALID-NAME" to "value"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeExecutionPayload.Argv("node", listOf("bad\u0000argument"))
        }
    }
}

