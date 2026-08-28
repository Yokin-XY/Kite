package com.kite.app.resources

import com.kite.app.run.CardRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceUiProjectorTest {
    @Test
    fun `安装生命周期状态投影是确定的`() {
        assertProjection(installed = false, state = "未获取", action = "获取", enabled = true)
        assertProjection(preparing = true, state = "准备中", action = "准备中", enabled = false)
        assertProjection(
            preparing = true,
            installPlanInProgress = true,
            state = "获取中",
            action = "获取中",
            enabled = true,
            secondary = "取消"
        )
        assertProjection(installing = true, state = "获取中", action = "获取中", enabled = true, secondary = "取消")
        assertProjection(installed = true, state = "已获取", action = "打开", enabled = true, secondary = "卸载")
        assertProjection(
            installed = true,
            updateAvailable = true,
            state = "可更新",
            action = "更新",
            enabled = true,
        )
        assertProjection(uninstalling = true, state = "卸载中", action = "卸载中", enabled = false)
        assertProjection(failed = true, state = "获取失败", action = "重新获取", enabled = true, secondary = "取消")
        assertProjection(
            failed = true,
            failedOperation = KiteResourceInstallStore.OP_UNINSTALL,
            state = "卸载失败",
            action = "重新获取",
            enabled = true
        )
    }

    @Test
    fun `运行生命周期决定详情页中止按钮是否存在`() {
        val starting = projection(installed = true, openRunStatus = CardRunStatus.Starting)
        assertEquals("启动中", starting.actionLabel)
        assertFalse(starting.actionEnabled)
        assertEquals(null, starting.secondaryActionLabel)

        listOf(CardRunStatus.WaitingTerminal, CardRunStatus.Running, CardRunStatus.AlreadyRunning, CardRunStatus.Opened)
            .forEach { status ->
                val running = projection(installed = true, openRunStatus = status)
                assertEquals("运行中", running.actionLabel)
                assertTrue(running.actionEnabled)
                assertEquals("中止", running.secondaryActionLabel)
            }

        val stopping = projection(installed = true, openRunStatus = CardRunStatus.Stopping)
        assertEquals("停止中", stopping.actionLabel)
        assertFalse(stopping.actionEnabled)
        assertEquals(null, stopping.secondaryActionLabel)

        val stopped = projection(installed = true, openRunStatus = CardRunStatus.Stopped)
        assertEquals("打开", stopped.actionLabel)
        assertTrue(stopped.actionEnabled)
        assertEquals("卸载", stopped.secondaryActionLabel)
    }

    private fun assertProjection(
        installed: Boolean = false,
        preparing: Boolean = false,
        installing: Boolean = false,
        installPlanInProgress: Boolean = false,
        uninstalling: Boolean = false,
        failed: Boolean = false,
        failedOperation: String = KiteResourceInstallStore.OP_INSTALL,
        updateAvailable: Boolean = false,
        state: String,
        action: String,
        enabled: Boolean,
        secondary: String? = null
    ) {
        val projection = projection(
            installed = installed,
            preparing = preparing,
            installing = installing,
            installPlanInProgress = installPlanInProgress,
            uninstalling = uninstalling,
            failed = failed,
            failedOperation = failedOperation,
            updateAvailable = updateAvailable,
        )
        assertEquals(state, projection.stateLabel)
        assertEquals(action, projection.actionLabel)
        assertEquals(enabled, projection.actionEnabled)
        assertEquals(secondary, projection.secondaryActionLabel)
    }

    private fun projection(
        installed: Boolean = false,
        preparing: Boolean = false,
        installing: Boolean = false,
        installPlanInProgress: Boolean = false,
        uninstalling: Boolean = false,
        failed: Boolean = false,
        failedOperation: String = KiteResourceInstallStore.OP_INSTALL,
        updateAvailable: Boolean = false,
        openRunStatus: CardRunStatus? = null
    ): KiteResourceUiProjection =
        KiteResourceUiProjector.project(
            installed = installed,
            preparing = preparing,
            installing = installing,
            installPlanInProgress = installPlanInProgress,
            uninstalling = uninstalling,
            failed = failed,
            failedOperation = failedOperation,
            idleStateLabel = "未获取",
            updateAvailable = updateAvailable,
            openRunStatus = openRunStatus
        )
}
