package com.kite.app.foundation.devicebridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceBridgeContractTest {
    @Test
    fun `capability ids are stable and unique`() {
        val definitions = DeviceBridgeCatalog.definitions

        assertEquals(definitions.size, definitions.map { it.id }.toSet().size)
        assertNotNull(DeviceBridgeCatalog.definition("shell.exec"))
        assertNotNull(DeviceBridgeCatalog.definition("package.install"))
        assertNotNull(DeviceBridgeCatalog.definition("screen.capture"))
    }

    @Test
    fun `backend that is not ready blocks every capability`() {
        val resolved = DeviceBridgeCatalog.resolve(
            DeviceBridgeRuntime(
                backend = DeviceBridgeBackend.Shizuku,
                identity = DeviceBridgeIdentity.Shell,
                lifecycle = DeviceBridgeLifecycleStatus.PermissionRequired,
                androidApi = 35
            )
        )

        assertTrue(resolved.isNotEmpty())
        assertTrue(resolved.all { it.support == DeviceBridgeCapabilitySupport.Blocked })
    }

    @Test
    fun `ready shell backend still requires real capability probes`() {
        val resolved = DeviceBridgeCatalog.resolve(
            DeviceBridgeRuntime(
                backend = DeviceBridgeBackend.Shizuku,
                identity = DeviceBridgeIdentity.Shell,
                lifecycle = DeviceBridgeLifecycleStatus.Ready,
                androidApi = 35
            )
        )

        assertTrue(resolved.all { it.support == DeviceBridgeCapabilitySupport.ProbeRequired })
        assertTrue(resolved.none { it.support == DeviceBridgeCapabilitySupport.Available })
    }

    @Test
    fun `shell identity never satisfies root only requirement`() {
        assertTrue(DeviceBridgeIdentity.Root.satisfies(DeviceBridgeIdentityRequirement.RootOnly))
        assertTrue(!DeviceBridgeIdentity.Shell.satisfies(DeviceBridgeIdentityRequirement.RootOnly))
    }

    @Test
    fun `destructive operations are explicitly classified`() {
        val destructive = DeviceBridgeCatalog.definitions
            .filter { it.risk == DeviceBridgeCapabilityRisk.Destructive }
            .map { it.id }

        assertTrue("package.uninstall" in destructive)
        assertTrue("package.clear_data" in destructive)
    }

    @Test
    fun `projected catalog preserves protocol risk and identity semantics`() {
        val catalog = JSONObject(DeviceBridgeCatalog.toJson())
        val capabilities = catalog.getJSONArray("capabilities")
        val install = (0 until capabilities.length())
            .map { capabilities.getJSONObject(it) }
            .single { it.getString("id") == "package.install" }

        assertEquals(DeviceBridgeContract.PROTOCOL_VERSION, catalog.getInt("protocolVersion"))
        assertEquals("sensitive", install.getString("risk"))
        assertEquals("shell_or_root", install.getString("identity"))
        assertEquals("probe_required", install.getString("defaultSupport"))
        assertTrue(
            (0 until catalog.getJSONArray("implementedCapabilityIds").length())
                .map { catalog.getJSONArray("implementedCapabilityIds").getString(it) }
                .contains("package.install")
        )
    }
}
