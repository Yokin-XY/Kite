package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCompatibilityRuntimeProviderTest {
    @Test
    fun `complex shell becomes a proot plan without parsing command text`() {
        val request = RuntimeExecutionRequest(
            payload = RuntimeExecutionPayload.CommandLine("for f in *.c; do gcc \"\$f\"; done"),
            workingDirectory = " /workspace/project ",
            environment = mapOf("TOKEN" to "private"),
            requirements = setOf(RuntimeExecutionRequirement.FULL_LINUX),
        )

        val decision = ProotCompatibilityRuntimeProvider.prepare(
            ProotCompatibilityProviderContext("full_linux_required"),
            request,
        ) as RuntimeProviderDecision.Ready

        assertEquals(RuntimeProviderKind.PROOT, decision.provider)
        assertEquals("full_linux_required", decision.reason)
        assertEquals(request.payload, decision.plan.payload)
        assertEquals("/workspace/project", decision.plan.workingDirectory)
        assertEquals(mapOf("TOKEN" to "private"), decision.plan.environment)
    }

    @Test
    fun `interactive view facts remain explicit in the logical plan`() {
        val decision = ProotCompatibilityRuntimeProvider.prepare(
            ProotCompatibilityProviderContext("filesystem_view_required"),
            RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv("/bin/bash", listOf("--login")),
                environment = mapOf(
                    ProotViewBinding.ENV_VIEW_ID to "update-view",
                    ProotViewBinding.ENV_ENVIRONMENT_ID to "environment-1",
                ),
                requirements = setOf(
                    RuntimeExecutionRequirement.FULL_LINUX,
                    RuntimeExecutionRequirement.INTERACTIVE_PTY,
                    RuntimeExecutionRequirement.FILESYSTEM_VIEW,
                ),
            ),
        ) as RuntimeProviderDecision.Ready

        assertTrue(decision.plan.interactivePty)
        assertEquals("update-view", decision.plan.requestedProotViewId)
        assertEquals("environment-1", decision.plan.requestedProotEnvironmentId)
    }

    @Test
    fun `structured argv with child process requirement remains compatible`() {
        val payload = RuntimeExecutionPayload.Argv("python3", listOf("task.py"))

        val decision = ProotCompatibilityRuntimeProvider.prepare(
            ProotCompatibilityProviderContext("python_child_process_required"),
            RuntimeExecutionRequest(
                payload = payload,
                requirements = setOf(RuntimeExecutionRequirement.CHILD_PROCESS),
            ),
        ) as RuntimeProviderDecision.Ready

        assertEquals(payload, decision.plan.payload)
        assertEquals(false, decision.plan.interactivePty)
    }

    @Test
    fun `android native capability cannot be disguised as proot fallback`() {
        val decision = ProotCompatibilityRuntimeProvider.prepare(
            ProotCompatibilityProviderContext("native_provider_unavailable"),
            RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.NativeCapability("network.download_sha256"),
                requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
            ),
        )

        assertTrue(decision is RuntimeProviderDecision.Blocked)
        assertEquals(
            "proot_cannot_execute_android_native_capability",
            (decision as RuntimeProviderDecision.Blocked).reason,
        )
    }
}
