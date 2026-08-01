package com.kite.app.platform.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedCommandNativeProofContractTest {
    private val containerManager = File(
        "src/main/kotlin/com/kite/app/foundation/runtime/KFContainerManager.kt"
    ).readText()
    private val gateway = File(
        "src/main/java/com/kite/app/platform/resources/AndroidResourceActionGateway.kt"
    ).readText()
    private val coordinator = File(
        "src/main/java/com/kite/app/platform/resources/ResourceManagedCommandEvidenceCoordinator.kt"
    ).readText()

    @Test
    fun nativeProofRequiresExecutableDefaultEnvironmentAndKeepsFallback() {
        assertTrue(containerManager.contains("Files.isExecutable(currentPath)"))
        assertTrue(containerManager.contains("executable = true"))
        assertTrue(gateway.contains("KiteResourceRegistry.DEFAULT_ENVIRONMENT_ID"))
        assertTrue(gateway.contains("buildResourceManagedCommandNativeProof"))
        assertTrue(coordinator.contains("nativeEnvironmentEligible"))
        assertTrue(coordinator.contains("pending.map(ResourceManagedCommandEvidenceRequest::requirement)"))
        assertFalse(coordinator.contains("openclaw"))
        assertFalse(coordinator.contains("kite.openclaw"))
    }
}
