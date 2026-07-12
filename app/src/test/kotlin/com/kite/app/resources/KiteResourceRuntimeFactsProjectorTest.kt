package com.kite.app.resources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceRuntimeFactsProjectorTest {
    @Test
    fun `完整目录和增量绑定共享计划忙碌判断`() {
        val plan = KiteResourcePlanSnapshot(
            targetResourceId = "tool",
            resourceIds = listOf("tool"),
            pendingResourceIds = listOf("tool"),
            stepStatusByResourceId = mapOf("tool" to "pending")
        )

        val facts = KiteResourceRuntimeFactsProjector.project("tool", null, plan)

        assertTrue(facts.installing)
        assertTrue(facts.extraBusy)
        assertFalse(facts.failed)
    }

    @Test
    fun `目标计划任一步失败会投影到目标资源`() {
        val plan = KiteResourcePlanSnapshot(
            targetResourceId = "target",
            resourceIds = listOf("base", "target"),
            stepStatusByResourceId = mapOf(
                "base" to KiteResourceInstallStore.PLAN_STEP_FAILED,
                "target" to "pending"
            )
        )

        val facts = KiteResourceRuntimeFactsProjector.project("target", null, plan)

        assertTrue(facts.failed)
        assertFalse(facts.installing)
    }

    @Test
    fun `本地基线安装与登记状态合并但不覆盖卸载`() {
        val uninstalling = KiteResourceRegistryEntry(
            resourceId = "node",
            status = KiteResourceRegistry.STATUS_UNINSTALLING,
            operation = KiteResourceInstallStore.OP_UNINSTALL
        )

        val facts = KiteResourceRuntimeFactsProjector.project(
            resourceId = "node",
            registryEntry = uninstalling,
            plan = KiteResourcePlanSnapshot(),
            baselineInstalled = true
        )

        assertTrue(facts.installed)
        assertTrue(facts.uninstalling)
        assertTrue(facts.extraBusy)
    }
}
