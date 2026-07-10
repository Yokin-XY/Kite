package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KiteResourceInstallStoreSignalTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `登记写入与信号快照必须在同一版本收敛`() {
        val store = KiteResourceInstallStore(context)
        val resourceId = "test.resource.signal.${System.nanoTime()}"
        store.clear(resourceId)

        store.markPreparing(resourceId)
        assertTrue(store.registryEntry(resourceId)?.preparing == true)
        assertEquals(KiteResourceInstallStore.STATUS_PREPARING, store.signals.value.status)

        store.markInstalling(resourceId, "install-run")
        assertTrue(store.registryEntry(resourceId)?.installing == true)
        assertEquals(KiteResourceInstallStore.STATUS_INSTALLING, store.signals.value.status)
        assertTrue(resourceId in store.signals.value.affectedResourceIds)

        store.markInstalled(resourceId, "1.0", "install-run", "done")
        assertTrue(store.registryEntry(resourceId)?.installed == true)
        assertEquals(KiteResourceInstallStore.STATUS_INSTALLED, store.signals.value.status)

        store.markUninstalling(resourceId, "uninstall-run")
        assertTrue(store.registryEntry(resourceId)?.uninstalling == true)
        assertEquals(KiteResourceInstallStore.OP_UNINSTALL, store.signals.value.operation)

        store.markFailed(resourceId, KiteResourceInstallStore.OP_UNINSTALL, "uninstall-run", "failed")
        assertTrue(store.registryEntry(resourceId)?.failed == true)
        assertEquals(KiteResourceInstallStore.STATUS_FAILED, store.signals.value.status)

        store.clear(resourceId)
        assertFalse(store.registrySnapshot(listOf(resourceId)).containsKey(resourceId))
        assertEquals("", store.signals.value.status)
    }

    @Test
    fun `执行队列变化必须携带全部受影响资源`() {
        val store = KiteResourceInstallStore(context)
        val suffix = System.nanoTime()
        val targetId = "test.resource.target.$suffix"
        val dependencyId = "test.resource.dependency.$suffix"
        store.clearPlan()

        store.beginPlan(targetId, listOf(dependencyId, targetId))
        assertEquals(listOf(dependencyId, targetId), store.planSnapshot().resourceIds)
        assertTrue(store.signals.value.affectedResourceIds.containsAll(listOf(dependencyId, targetId)))

        assertTrue(store.markPlanStepRunning(dependencyId))
        assertEquals(KiteResourceInstallStore.PLAN_STEP_RUNNING, store.planStepStatus(dependencyId))
        assertTrue(dependencyId in store.signals.value.affectedResourceIds)

        store.clearPlan()
        assertTrue(store.planSnapshot().resourceIds.isEmpty())
        assertTrue(store.signals.value.affectedResourceIds.containsAll(listOf(dependencyId, targetId)))
    }
}
