package com.kite.app.platform.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidPackageResourceFactsReconcilerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `包管理器已安装事实可以恢复资源登记`() {
        val environmentId = "android-package-ready-${System.nanoTime()}"
        val store = KiteResourceInstallStore(context, environmentId)
        val manifest = manifest()
        val reconciler = AndroidPackageResourceFactsReconciler(
            installStore = store,
            packageStateProbe = AndroidPackageStateProbe { it == manifest.source.packageName },
        )

        val result = reconciler.reconcile(listOf(manifest), environmentId)

        assertEquals(setOf(manifest.id), result.readyResourceIds)
        assertEquals(setOf(manifest.id), result.restoredResourceIds)
        assertTrue(store.isInstalled(manifest.id, environmentId))
    }

    @Test
    fun `包被移除时不继续冒充可用资源`() {
        val environmentId = "android-package-missing-${System.nanoTime()}"
        val store = KiteResourceInstallStore(context, environmentId)
        val manifest = manifest()
        store.markInstalled(manifest.id, manifest.version, null, "installed", environmentId)
        val reconciler = AndroidPackageResourceFactsReconciler(
            installStore = store,
            packageStateProbe = AndroidPackageStateProbe { false },
        )

        val result = reconciler.reconcile(listOf(manifest), environmentId)

        assertEquals(setOf(manifest.id), result.missingResourceIds)
        assertFalse(result.readyResourceIds.contains(manifest.id))
        assertTrue(store.isInstalled(manifest.id, environmentId))
        assertEquals(
            KiteResourceInstallStore.UPDATE_STATUS_FAILED,
            store.registryEntry(manifest.id, environmentId)?.updateStatus,
        )
        assertEquals(
            KiteResourceInstallStore.OP_REPAIR,
            store.registryEntry(manifest.id, environmentId)?.operation,
        )
    }

    private fun manifest() = KiteResourceManifestLoader(context).parseManifestJson(
        """
            {
              "schemaVersion": 1,
              "id": "test.android.package",
              "base": {"name": "Android package", "description": "test", "version": "1.2.3"},
              "management": {"mode": "managed_extension"},
              "display": {"sections": ["more"]},
              "relations": {"provides": [], "base": [], "defaults": [], "extensions": []},
              "source": {"type": "android_apk", "package": "example.android.package"}
            }
        """.trimIndent(),
    )
}
