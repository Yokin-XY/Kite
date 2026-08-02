package com.kite.app.platform.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.resources.KiteResourceManifestLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceOpenDependencyResolverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun codexOpenClosureIncludesRelayAndItsRuntimeDependencies() {
        val loader = KiteResourceManifestLoader(context, isDebugBuild = true)
        val manifests = loader.manifests().values
        val installed = setOf(
            "kite.codex.cli",
            "kite.codex.relay",
            "kite.nodejs",
            "kite.git",
            "kite.python",
            "kite.uv",
        )

        val closure = ResourceOpenDependencyResolver.resolve(
            targetResourceId = "kite.codex.cli",
            manifests = manifests,
            installedResourceIds = installed,
            relationTargetsFor = loader::requestRelationTargets,
        )

        assertTrue(closure.manifests.map { it.id }.containsAll(installed))
        assertEquals(emptySet<String>(), closure.missingInstalledResourceIds)
        assertEquals(emptySet<String>(), closure.unresolvedRequirements)
    }

    @Test
    fun missingInstalledDependencyRequestsRepairBeforeOpen() {
        val loader = KiteResourceManifestLoader(context, isDebugBuild = true)
        val closure = ResourceOpenDependencyResolver.resolve(
            targetResourceId = "kite.codex.cli",
            manifests = loader.manifests().values,
            installedResourceIds = setOf("kite.codex.cli", "kite.nodejs", "kite.git"),
            relationTargetsFor = loader::requestRelationTargets,
        )

        assertTrue("kite.codex.relay" in closure.missingInstalledResourceIds)
        assertTrue("kite.python" in closure.missingInstalledResourceIds)
        assertTrue("kite.uv" in closure.missingInstalledResourceIds)
    }
}
