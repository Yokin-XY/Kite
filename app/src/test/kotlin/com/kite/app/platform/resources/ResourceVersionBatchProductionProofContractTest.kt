package com.kite.app.platform.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceVersionBatchProductionProofContractTest {
    private val source = File(
        "src/debug/kotlin/com/kite/app/platform/resources/ResourceVersionBatchProductionProofReceiver.kt"
    ).readText()
    private val manifest = File("src/debug/AndroidManifest.xml").readText()

    @Test
    fun proofUsesInstalledProductionFactsAndCannotAcceptAdbTargets() {
        assertTrue(source.contains("RESOURCE_VERSION_BATCH_PRODUCTION_PROOF"))
        assertTrue(source.contains("registrySnapshot()"))
        assertTrue(source.contains("entry.installed"))
        assertTrue(source.contains("KiteResourceSourcePlanFactory.versionCheckPlan"))
        assertTrue(source.contains("plan.installed?.structuredMetadata"))
        assertTrue(source.contains("plan.latest is KiteResourceRemoteVersionProbe"))
        assertTrue(source.contains("resourceActionWorkflowCoordinator.checkUpdates"))
        assertTrue(source.contains("selected.size == 2"))
        assertTrue(source.contains("checkingRemaining == 0"))
        assertTrue(source.contains("productionWorkflow=true"))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getStringArrayExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("kite.openclaw"))
        assertTrue(manifest.contains("com.kite.app.debug.RESOURCE_VERSION_BATCH_PRODUCTION_PROOF"))
        assertTrue(manifest.contains("ResourceVersionBatchProductionProofService"))
    }
}
