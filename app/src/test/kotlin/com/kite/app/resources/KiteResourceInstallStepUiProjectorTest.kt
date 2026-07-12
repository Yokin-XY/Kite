package com.kite.app.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceInstallStepUiProjectorTest {
    @Test
    fun `卸载状态优先于其他安装事实`() {
        val projection = project(uninstalling = true, installed = true)

        assertEquals("卸载中", projection.statusLabel)
        assertEquals(KiteResourceStepTone.Primary, projection.tone)
        assertTrue(projection.uninstalling)
    }

    @Test
    fun `失败与阻塞分别投影为需卸载和已暂停`() {
        val failed = project(failed = true)
        val blocked = project(planStepStatus = KiteResourceInstallStore.PLAN_STEP_BLOCKED)

        assertEquals("需卸载", failed.statusLabel)
        assertTrue(failed.failed)
        assertEquals(KiteResourceStepTone.Danger, failed.tone)
        assertEquals("已暂停", blocked.statusLabel)
        assertFalse(blocked.failed)
    }

    @Test
    fun `运行完成和等待状态保持确定优先级`() {
        assertEquals(
            "获取中",
            project(planStepStatus = KiteResourceInstallStore.PLAN_STEP_RUNNING, installed = true).statusLabel
        )
        assertEquals("已完成", project(installed = true).statusLabel)
        assertEquals("待获取", project(isActive = true).statusLabel)
    }

    private fun project(
        uninstalling: Boolean = false,
        failed: Boolean = false,
        failedOperation: String = KiteResourceInstallStore.OP_INSTALL,
        planStepStatus: String = "",
        installed: Boolean = false,
        isActive: Boolean = false
    ): KiteResourceInstallStepUiProjection = KiteResourceInstallStepUiProjector.project(
        uninstalling,
        failed,
        failedOperation,
        planStepStatus,
        installed,
        isActive
    )
}
