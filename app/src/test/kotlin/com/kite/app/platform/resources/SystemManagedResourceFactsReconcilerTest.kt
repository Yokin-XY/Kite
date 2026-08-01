package com.kite.app.platform.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManagementMode
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemManagedResourceFactsReconcilerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val loader = KiteResourceManifestLoader(context)

    @Test
    fun `真实就绪的缺失和失效系统组件恢复登记并从计划排除`() = runBlocking {
        val environmentId = "system-ready-${System.nanoTime()}"
        val store = KiteResourceInstallStore(context, environmentId)
        val missingRegistration = manifest("test.system.missing", KiteResourceManagementMode.SYSTEM_COMPONENT)
        val failedRegistration = manifest("test.system.failed", KiteResourceManagementMode.SYSTEM_COMPONENT)
        val target = manifest("test.extension.target", KiteResourceManagementMode.MANAGED_EXTENSION)
        store.markFailed(
            resourceId = failedRegistration.id,
            operation = KiteResourceInstallStore.OP_INSTALL,
            runId = "failed-run",
            reason = "stale registration",
            environmentId = environmentId,
        )
        val probe = RecordingProbe(missingResourceIds = emptySet())
        val reconciler = SystemManagedResourceFactsReconciler(store, probe)

        val result = reconciler.reconcile(
            manifests = listOf(missingRegistration, failedRegistration, target),
            environmentId = environmentId,
        ).getOrThrow()

        assertEquals(setOf(missingRegistration.id, failedRegistration.id), result.readyResourceIds)
        assertEquals(result.readyResourceIds, result.restoredResourceIds)
        assertTrue(store.isInstalled(missingRegistration.id, environmentId))
        assertTrue(store.isInstalled(failedRegistration.id, environmentId))
        assertEquals(missingRegistration.version, store.registryEntry(missingRegistration.id, environmentId)?.version)
        assertEquals(
            listOf(target.id),
            pendingInstallPlanResourceIds(
                resourceIds = listOf(missingRegistration.id, failedRegistration.id, target.id),
                targetResourceId = target.id,
                installedResourceIds = setOf(missingRegistration.id, failedRegistration.id),
            ),
        )
        assertEquals(setOf(missingRegistration.id, failedRegistration.id), probe.requestedResourceIds)
    }

    @Test
    fun `真实命令未就绪的系统组件保留在安装计划`() = runBlocking {
        val environmentId = "system-missing-${System.nanoTime()}"
        val store = KiteResourceInstallStore(context, environmentId)
        val system = manifest("test.system.not-ready", KiteResourceManagementMode.SYSTEM_COMPONENT)
        val target = manifest("test.extension.target", KiteResourceManagementMode.MANAGED_EXTENSION)
        val reconciler = SystemManagedResourceFactsReconciler(
            installStore = store,
            installedStateProbe = RecordingProbe(missingResourceIds = setOf(system.id)),
        )

        val result = reconciler.reconcile(listOf(system, target), environmentId).getOrThrow()

        assertTrue(result.readyResourceIds.isEmpty())
        assertTrue(result.restoredResourceIds.isEmpty())
        assertFalse(store.isInstalled(system.id, environmentId))
        assertEquals(
            listOf(system.id, target.id),
            pendingInstallPlanResourceIds(
                resourceIds = listOf(system.id, target.id),
                targetResourceId = target.id,
                installedResourceIds = emptySet(),
            ),
        )
    }

    @Test
    fun `未登记的普通扩展不参与系统组件事实恢复`() = runBlocking {
        val environmentId = "extension-unaffected-${System.nanoTime()}"
        val store = KiteResourceInstallStore(context, environmentId)
        val extension = manifest("test.extension.unaffected", KiteResourceManagementMode.MANAGED_EXTENSION)
        val probe = RecordingProbe(missingResourceIds = emptySet())
        val reconciler = SystemManagedResourceFactsReconciler(store, probe)

        val result = reconciler.reconcile(listOf(extension), environmentId).getOrThrow()

        assertTrue(result.readyResourceIds.isEmpty())
        assertTrue(result.restoredResourceIds.isEmpty())
        assertFalse(store.isInstalled(extension.id, environmentId))
        assertTrue(probe.requestedResourceIds.isEmpty())
        assertEquals(
            listOf(extension.id),
            pendingInstallPlanResourceIds(
                resourceIds = listOf(extension.id),
                targetResourceId = extension.id,
                installedResourceIds = emptySet(),
            ),
        )
    }

    @Test
    fun `已有系统组件版本不因命令存在而被伪造升级`() = runBlocking {
        val environmentId = "system-version-${System.nanoTime()}"
        val store = KiteResourceInstallStore(context, environmentId)
        val system = manifest("test.system.old-version", KiteResourceManagementMode.SYSTEM_COMPONENT)
        store.markInstalled(
            resourceId = system.id,
            version = "1.0.0",
            runId = "bootstrap-old",
            summary = "old bundled component",
            environmentId = environmentId,
        )
        val probe = RecordingProbe(missingResourceIds = emptySet())
        val reconciler = SystemManagedResourceFactsReconciler(store, probe)

        val result = reconciler.reconcile(listOf(system), environmentId).getOrThrow()

        assertTrue(result.restoredResourceIds.isEmpty())
        assertTrue(probe.requestedResourceIds.isEmpty())
        assertEquals("1.0.0", store.registryEntry(system.id, environmentId)?.version)
    }

    private fun manifest(id: String, mode: KiteResourceManagementMode): KiteResourceManifest =
        loader.parseManifestJson(
            """
                {
                  "schemaVersion": 2,
                  "id": "$id",
                  "base": {
                    "name": "$id",
                    "description": "test resource",
                    "version": "1.2.3"
                  },
                  "display": {"sections": ["foundation"]},
                  "management": {
                    "mode": "${mode.wireValue}",
                    "managedCommands": ["${id.substringAfterLast('.')}"]
                  },
                  "relations": {"provides": [], "base": [], "defaults": [], "extensions": []},
                  "source": {"type": "bundled"}
                }
            """.trimIndent()
        )

    private class RecordingProbe(
        private val missingResourceIds: Set<String>,
    ) : ResourceInstalledStateProbe {
        val requestedResourceIds = linkedSetOf<String>()

        override suspend fun missingResourceIds(
            requirements: Collection<ResourceManagedCommandRequirement>,
        ): Result<Set<String>> {
            requestedResourceIds += requirements.map(ResourceManagedCommandRequirement::resourceId)
            return Result.success(missingResourceIds)
        }
    }
}
