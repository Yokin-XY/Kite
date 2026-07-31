package com.kite.app.foundation.capability

import com.kite.app.foundation.runtime.AndroidNativeArchiveCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeDownloadCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeFileCapabilityProvider
import com.kite.app.recipe.KiteRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityCatalogRoutingTest {
    @Test
    fun `routable ids are unique and native recipe results stay in CardRunStore`() {
        val entries = CapabilityCatalog.routableEntries
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        entries.filter { it.invocation == CapabilityInvocationKind.NATIVE_RECIPE }.forEach { entry ->
            assertEquals(CapabilityResultOwner.CARD_RUN_STORE, entry.resultOwner)
            assertEquals(CapabilityFallbackBoundary.NEVER_AUTOMATIC, entry.fallbackBoundary)
        }
        assertTrue(CapabilityCatalog.routableEntryFor(AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID)!!.automaticResourceRouting)
        assertFalse(CapabilityCatalog.routableEntryFor(AndroidNativeArchiveCapabilityProvider.CAPABILITY_ID)!!.automaticResourceRouting)
    }

    @Test
    fun `destructive file capabilities require root policy`() {
        listOf(
            AndroidNativeFileCapabilityProvider.CAPABILITY_MOVE_FILE,
            AndroidNativeFileCapabilityProvider.CAPABILITY_DELETE_FILE,
        ).forEach { id ->
            val entry = CapabilityCatalog.routableEntryFor(id)!!
            assertTrue(CapabilityPermissionGate.FILE_ROOT_POLICY in entry.permissionGates)
            assertEquals(CapabilityFallbackBoundary.NEVER_AUTOMATIC, entry.fallbackBoundary)
        }
    }

    @Test
    fun `APK action is an external handoff and never claims install completion`() {
        val entry = CapabilityCatalog.routableEntryForLegacyAction(KiteRecipe.ANDROID_ACTION_INSTALL_APK)!!
        assertEquals(CapabilityCatalog.CAPABILITY_OPEN_APK_INSTALLER, entry.id)
        assertEquals(CapabilityInvocationKind.ANDROID_ACTION, entry.invocation)
        assertEquals(CapabilityResultOwner.EXTERNAL_ANDROID_INSTALLER, entry.resultOwner)
        assertEquals(CapabilityCompletionKind.EXTERNAL_HANDOFF, entry.completion)
        assertTrue(CapabilityPermissionGate.USER_CONFIRMATION in entry.permissionGates)
    }

    @Test
    fun `network and permission capabilities keep their existing owners`() {
        val network = CapabilityCatalog.routableEntryFor(CapabilityCatalog.CAPABILITY_DEFAULT_NETWORK_ALIGNMENT)!!
        assertEquals(CapabilityInvocationKind.LIFECYCLE_SERVICE, network.invocation)
        assertEquals(CapabilityResultOwner.DEFAULT_NETWORK_ALIGNMENT, network.resultOwner)
        val permissions = CapabilityCatalog.routableEntryFor(CapabilityCatalog.CAPABILITY_RUNTIME_PERMISSION_SNAPSHOT)!!
        assertEquals(CapabilityInvocationKind.QUERY_ONLY, permissions.invocation)
        assertEquals(CapabilityResultOwner.RUNTIME_BOOTSTRAP_GATEWAY, permissions.resultOwner)
    }

    @Test
    fun `catalog does not invent missing keystore provider`() {
        assertNull(CapabilityCatalog.routableEntryFor("android.keystore.secret"))
    }
}
