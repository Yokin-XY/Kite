package com.kite.app.platform.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceVersionProductionRouteProofContractTest {
    private val source = File(
        "src/debug/kotlin/com/kite/app/platform/resources/ResourceVersionProductionRouteProofReceiver.kt"
    ).readText()
    private val manifest = File("src/debug/AndroidManifest.xml").readText()

    @Test
    fun `证明入口从真实安装登记按合同选取且 ADB 不能指定样例`() {
        assertTrue(source.contains("resourceInstallStore.registrySnapshot()"))
        assertTrue(source.contains("entry.installed"))
        assertTrue(source.contains("KiteResourceSourcePlanFactory.versionCheckPlan"))
        assertTrue(source.contains("resourceActionWorkflowCoordinator.dispatch"))
        assertTrue(source.contains("structuredMetadata != null"))
        assertTrue(source.contains("KiteResourceActionIntent.CheckUpdate"))
        assertTrue(source.contains("nativeChecks=1 fallbackChecks=1"))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("requestManifest(\""))
        assertFalse(source.contains("kite.openclaw"))
        assertFalse(source.contains("kite.codex.cli"))
        assertTrue(manifest.contains("com.kite.app.debug.RESOURCE_VERSION_PRODUCTION_ROUTE_PROOF"))
        assertTrue(manifest.contains("ResourceVersionProductionRouteProofService"))
    }
}
