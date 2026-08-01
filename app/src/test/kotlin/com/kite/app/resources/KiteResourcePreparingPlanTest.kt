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
class KiteResourcePreparingPlanTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `准备计划没有步骤时仍持久化目标且同目标写入幂等`() {
        val environmentId = "preparing-idempotent-${System.nanoTime()}"
        val targetId = "test.resource.preparing.${System.nanoTime()}"
        val otherId = "$targetId.other"
        val store = KiteResourceInstallStore(context, environmentId)
        store.clearPlan(environmentId)

        assertTrue(store.beginPreparingPlan(targetId, environmentId))
        val first = store.planSnapshot(environmentId)
        assertEquals(targetId, first.targetResourceId)
        assertTrue(first.isPreparing)
        assertFalse(first.isActive)
        assertTrue(first.resourceIds.isEmpty())

        assertTrue(store.beginPreparingPlan(targetId, environmentId))
        assertEquals(first, store.planSnapshot(environmentId))
        assertFalse(store.beginPreparingPlan(otherId, environmentId))
        assertEquals(first, store.planSnapshot(environmentId))

        store.clearPlan(environmentId)
    }

    @Test
    fun `准备计划只允许同目标原子激活且活动计划不可覆盖`() {
        val environmentId = "preparing-activate-${System.nanoTime()}"
        val targetId = "test.resource.target.${System.nanoTime()}"
        val dependencyId = "test.resource.dependency.${System.nanoTime()}"
        val otherId = "$targetId.other"
        val store = KiteResourceInstallStore(context, environmentId)
        store.clearPlan(environmentId)

        assertTrue(store.beginPreparingPlan(targetId, environmentId))
        assertFalse(store.activatePreparedPlan(otherId, listOf(otherId), environmentId))
        val stillPreparing = store.planSnapshot(environmentId)
        assertEquals(targetId, stillPreparing.targetResourceId)
        assertTrue(stillPreparing.isPreparing)
        assertTrue(stillPreparing.resourceIds.isEmpty())

        assertTrue(
            store.activatePreparedPlan(
                targetId,
                listOf(dependencyId, targetId, dependencyId),
                environmentId
            )
        )
        val active = store.planSnapshot(environmentId)
        assertEquals(targetId, active.targetResourceId)
        assertTrue(active.isActive)
        assertFalse(active.isPreparing)
        assertEquals(listOf(dependencyId, targetId), active.resourceIds)
        assertEquals(listOf(dependencyId, targetId), active.pendingResourceIds)

        assertFalse(store.activatePreparedPlan(targetId, listOf(otherId), environmentId))
        assertFalse(store.beginPreparingPlan(targetId, environmentId))
        assertFalse(store.beginPreparingPlan(otherId, environmentId))
        assertEquals(active, store.planSnapshot(environmentId))

        store.clearPlan(environmentId)
    }

    @Test
    fun `准备与激活计划按环境隔离`() {
        val suffix = System.nanoTime()
        val firstEnvironment = "preparing-env-a-$suffix"
        val secondEnvironment = "preparing-env-b-$suffix"
        val firstTarget = "test.resource.first.$suffix"
        val secondTarget = "test.resource.second.$suffix"
        val store = KiteResourceInstallStore(context, firstEnvironment)
        store.clearPlan(firstEnvironment)
        store.clearPlan(secondEnvironment)

        assertTrue(store.beginPreparingPlan(firstTarget, firstEnvironment))
        assertTrue(store.beginPreparingPlan(secondTarget, secondEnvironment))
        assertTrue(store.activatePreparedPlan(firstTarget, listOf(firstTarget), firstEnvironment))

        val first = store.planSnapshot(firstEnvironment)
        val second = store.planSnapshot(secondEnvironment)
        assertEquals(firstTarget, first.targetResourceId)
        assertTrue(first.isActive)
        assertEquals(listOf(firstTarget), first.resourceIds)
        assertEquals(secondTarget, second.targetResourceId)
        assertTrue(second.isPreparing)
        assertTrue(second.resourceIds.isEmpty())

        store.clearPlan(firstEnvironment)
        store.clearPlan(secondEnvironment)
    }
}
