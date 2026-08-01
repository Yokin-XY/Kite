package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultContainerColdReuseProviderTest {
    @Test
    fun completeOrdinaryFactsAreReady() {
        val identity = identity()

        val decision = DefaultContainerColdReuseProvider.evaluate(readyFacts(identity))

        assertTrue(decision is DefaultContainerColdReuseDecision.Ready)
        assertSame(identity, (decision as DefaultContainerColdReuseDecision.Ready).identity)
    }

    @Test
    fun explicitViewOrEnvironmentIsUnsupportedBeforeIdentityUse() {
        val decision = DefaultContainerColdReuseProvider.evaluate(
            readyFacts().copy(ordinaryRequest = false),
        )

        assertEquals(
            "explicit_view_or_environment",
            (decision as DefaultContainerColdReuseDecision.Unsupported).reason,
        )
    }

    @Test
    fun packagedRuntimeChangeFallsBackToFullPreparation() {
        assertUnsupportedFact(readyFacts().copy(runtimeAssetsCurrent = false), "runtime_assets")
    }

    @Test
    fun baseImageMismatchFallsBackToFullPreparation() {
        assertUnsupportedFact(readyFacts().copy(baseImageReady = false), "base_image")
    }

    @Test
    fun containerRecordMismatchFallsBackToFullPreparation() {
        assertUnsupportedFact(readyFacts().copy(containerRecordCurrent = false), "container_record")
    }

    @Test
    fun rootfsMarkerOrRequiredFileMismatchFallsBackToFullPreparation() {
        assertUnsupportedFact(readyFacts().copy(containerRootfsReady = false), "container_rootfs")
    }

    @Test
    fun missingWorkspaceFallsBackToFullPreparation() {
        assertUnsupportedFact(readyFacts().copy(workspaceReady = false), "workspace")
    }

    @Test
    fun staleMutableRepairReceiptFallsBackToFullPreparation() {
        assertUnsupportedFact(readyFacts().copy(mutableRepairCurrent = false), "mutable_repair")
    }

    @Test
    fun invalidStructuredIdentityIsBlocked() {
        val decision = DefaultContainerColdReuseProvider.evaluate(
            readyFacts(identity(runtimeDescriptorStamp = 0L)),
        )

        assertEquals(
            "default_container_identity_invalid",
            (decision as DefaultContainerColdReuseDecision.Blocked).reason,
        )
    }

    private fun assertUnsupportedFact(facts: DefaultContainerColdReuseFacts, expected: String) {
        val decision = DefaultContainerColdReuseProvider.evaluate(facts)
        val reason = (decision as DefaultContainerColdReuseDecision.Unsupported).reason
        assertTrue(reason, reason.contains(expected))
    }

    private fun readyFacts(
        identity: RuntimeLaunchPreparationIdentity = identity(),
    ) = DefaultContainerColdReuseFacts(
        ordinaryRequest = true,
        runtimeAssetsCurrent = true,
        baseImageReady = true,
        containerRecordCurrent = true,
        containerRootfsReady = true,
        workspaceReady = true,
        mutableRepairCurrent = true,
        identity = identity,
    )

    private fun identity(
        runtimeDescriptorStamp: Long = 101L,
    ) = RuntimeLaunchPreparationIdentity(
        runtimeRootPath = "/runtime",
        runtimeDescriptorStamp = runtimeDescriptorStamp,
        containerId = "ubuntu-main",
        containerCreatedAtMs = 100L,
        rootfsPath = "/runtime/containers/ubuntu-main/rootfs",
        workspacePath = "/runtime/shared/ubuntu-main",
        networkMode = "HOST",
    )
}
