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

    @Test
    fun `动作启动前被拒绝会重新发布原事实供页面撤销确认态`() {
        val store = KiteResourceInstallStore(context)
        val resourceId = "test.resource.republish.${System.nanoTime()}"
        store.clear(resourceId)
        store.markInstalled(resourceId, "1.0.0", "install-run", "done")
        val before = store.signals.value.revision

        store.republish(resourceId, "maintenanceOperationRejected")

        assertTrue(store.signals.value.revision > before)
        assertEquals("maintenanceOperationRejected", store.signals.value.reason)
        assertEquals(KiteResourceInstallStore.STATUS_INSTALLED, store.signals.value.status)
        assertTrue(store.registryEntry(resourceId)?.installed == true)
        store.clear(resourceId)
    }
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

    @Test
    fun `连续信号只保留最新事件但共享快照保留全部事实`() {
        val store = KiteResourceInstallStore(context)
        val suffix = System.nanoTime()
        val installingId = "test.resource.installing.$suffix"
        val failedId = "test.resource.failed.$suffix"

        store.clear(installingId)
        store.clear(failedId)
        store.markInstalling(installingId, "install-run")
        val installingRevision = store.signals.value.revision
        store.markFailed(failedId, KiteResourceInstallStore.OP_INSTALL, "failed-run", "network")

        val latestSignal = store.signals.value
        assertTrue(latestSignal.revision > installingRevision)
        assertEquals(listOf(failedId), latestSignal.affectedResourceIds)
        assertTrue(store.registryEntry(installingId)?.installing == true)
        assertTrue(store.registryEntry(failedId)?.failed == true)

        store.clear(installingId)
        store.clear(failedId)
    }

    @Test
    fun `版本查询状态不能覆盖已安装事实`() {
        val store = KiteResourceInstallStore(context)
        val resourceId = "test.resource.version.${System.nanoTime()}"
        store.clear(resourceId)
        store.markInstalled(resourceId, "1.0.0", "install-run", "done")

        store.markUpdateChecking(resourceId)
        assertTrue(store.registryEntry(resourceId)?.installed == true)
        assertEquals(KiteResourceInstallStore.UPDATE_STATUS_CHECKING, store.registryEntry(resourceId)?.updateStatus)

        store.markUpdateAvailable(resourceId, "1.0.0", "1.1.0")
        val available = store.registryEntry(resourceId)
        assertTrue(available?.installed == true)
        assertEquals("1.0.0", available?.version)
        assertEquals("1.1.0", available?.latestVersion)
        assertEquals(KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE, available?.updateStatus)
        assertTrue((available?.lastCheckedAt ?: 0L) > 0L)

        store.markUpdateCheckFailed(resourceId, "network")
        val failed = store.registryEntry(resourceId)
        assertTrue(failed?.installed == true)
        assertEquals(KiteResourceInstallStore.STATUS_INSTALLED, failed?.status)
        assertEquals(KiteResourceInstallStore.UPDATE_STATUS_FAILED, failed?.updateStatus)

        store.markInstalling(resourceId, operation = KiteResourceInstallRecipes.OP_UPDATE)
        assertTrue(store.registryEntry(resourceId)?.installing == true)
        assertTrue(store.registryEntry(resourceId)?.installed == true)
        assertTrue(store.isInstalled(resourceId))
        store.markMaintenanceFailed(resourceId, KiteResourceInstallRecipes.OP_UPDATE, "download")
        val updateFailed = store.registryEntry(resourceId)
        assertTrue(updateFailed?.installed == true)
        assertEquals(KiteResourceInstallStore.STATUS_INSTALLED, updateFailed?.status)
        assertEquals(KiteResourceInstallRecipes.OP_UPDATE, updateFailed?.operation)
        assertEquals("1.0.0", updateFailed?.version)

        store.clear(resourceId)
    }

    @Test
    fun `资源事实与安装计划按环境隔离且切换只投影目标环境`() {
        val suffix = System.nanoTime()
        val resourceId = "test.resource.environment.$suffix"
        val defaultEnvironment = KiteResourceRegistry.DEFAULT_ENVIRONMENT_ID
        val secondEnvironment = "profile-$suffix"
        val store = KiteResourceInstallStore(context, defaultEnvironment)

        store.clear(resourceId, defaultEnvironment)
        store.clear(resourceId, secondEnvironment)
        store.clearPlan(defaultEnvironment)
        store.clearPlan(secondEnvironment)
        store.markInstalled(resourceId, "1.0.0", "default-run", "default", defaultEnvironment)
        store.beginPlan(resourceId, listOf(resourceId), defaultEnvironment)

        store.activateEnvironment(secondEnvironment)
        assertEquals(secondEnvironment, store.currentEnvironmentId())
        assertFalse(store.isInstalled(resourceId))
        assertTrue(store.planSnapshot().resourceIds.isEmpty())
        assertEquals(secondEnvironment, store.signals.value.environmentId)

        store.markFailed(
            resourceId,
            KiteResourceInstallStore.OP_INSTALL,
            "profile-run",
            "profile failure"
        )
        store.beginPlan(resourceId, listOf(resourceId))
        assertTrue(store.isFailed(resourceId, secondEnvironment))
        assertTrue(store.isInstalled(resourceId, defaultEnvironment))
        assertEquals("1.0.0", store.registryEntry(resourceId, defaultEnvironment)?.version)
        assertEquals(listOf(resourceId), store.planSnapshot(secondEnvironment).resourceIds)
        assertEquals(listOf(resourceId), store.planSnapshot(defaultEnvironment).resourceIds)

        store.activateEnvironment(defaultEnvironment)
        assertTrue(store.isInstalled(resourceId))
        assertFalse(store.isFailed(resourceId))
        assertEquals(defaultEnvironment, store.signals.value.environmentId)

        store.clear(resourceId, defaultEnvironment)
        store.clear(resourceId, secondEnvironment)
        store.clearPlan(defaultEnvironment)
        store.clearPlan(secondEnvironment)
    }

    @Test
    fun `缺失安装事实必须批量失效且只发出一次共享信号`() {
        val store = KiteResourceInstallStore(context)
        val suffix = System.nanoTime()
        val first = "test.resource.missing.first.$suffix"
        val second = "test.resource.missing.second.$suffix"
        store.clear(first)
        store.clear(second)
        store.markInstalled(first, "1.0.0", "first-run", "done")
        store.markInstalled(second, "2.0.0", "second-run", "done")
        store.saveInstalledSnapshot(first, "First", "{}", "1.0.0", "{\"id\":\"$first\"}")
        store.saveInstalledSnapshot(second, "Second", "{}", "2.0.0", "{\"id\":\"$second\"}")
        val previousRevision = store.signals.value.revision

        store.invalidateMissingInstallations(listOf(first, second))

        assertFalse(store.isInstalled(first))
        assertFalse(store.isInstalled(second))
        assertEquals(null, store.installedSnapshotManifestJson(first))
        assertEquals(null, store.installedSnapshotManifestJson(second))
        assertEquals(previousRevision + 1, store.signals.value.revision)
        assertEquals(setOf(first, second), store.signals.value.affectedResourceIds.toSet())
        assertEquals("invalidateMissingInstallations", store.signals.value.reason)
    }

    @Test
    fun `启动时清理没有执行队列承接的孤儿准备状态`() {
        val environmentId = "orphan-preparing-${System.nanoTime()}"
        val orphaned = "test.resource.orphaned.${System.nanoTime()}"
        val registry = KiteResourceRegistry(context)
        registry.clear(orphaned, environmentId)
        registry.clearPlan(environmentId)
        registry.markPreparing(orphaned, environmentId)

        val store = KiteResourceInstallStore(context, environmentId)

        assertFalse(store.isPreparing(orphaned, environmentId))
        assertEquals("reconcileOrphanedPreparingState", store.signals.value.reason)
        assertTrue(orphaned in store.signals.value.affectedResourceIds)
    }

    @Test
    fun `启动时清理没有进程所有者的准备计划`() {
        val environmentId = "planned-preparing-${System.nanoTime()}"
        val planned = "test.resource.planned.${System.nanoTime()}"
        val registry = KiteResourceRegistry(context)
        registry.clear(planned, environmentId)
        registry.clearPlan(environmentId)
        registry.markPreparing(planned, environmentId)
        assertTrue(registry.beginPreparingPlan(planned, environmentId))

        val store = KiteResourceInstallStore(context, environmentId)

        assertFalse(store.isPreparing(planned, environmentId))
        assertTrue(store.planSnapshot(environmentId).targetResourceId.isBlank())
        assertFalse(store.planSnapshot(environmentId).isPreparing)
        assertTrue(store.planSnapshot(environmentId).resourceIds.isEmpty())
        store.clear(planned, environmentId)
        store.clearPlan(environmentId)
    }
}
