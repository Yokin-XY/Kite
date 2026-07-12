package com.kite.app.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteInstallPlanActionCoordinatorTest {
    @Test
    fun `运行和卸载中的计划不可重复提交`() {
        val running = plan(hasRunningStep = true)
        val uninstalling = plan(hasUninstallingStep = true)

        assertFalse(running.enabled)
        assertEquals("获取中", running.label)
        assertFalse(uninstalling.enabled)
        assertEquals("卸载中", uninstalling.label)
    }

    @Test
    fun `失败计划只暴露解释状态`() {
        val plan = plan(hasFailure = true)

        assertFalse(plan.enabled)
        assertEquals(null, plan.intent)
    }

    @Test
    fun `待执行计划提交开始下一项`() {
        val plan = plan(hasPending = true)

        assertTrue(plan.enabled)
        assertEquals(KiteInstallPlanActionIntent.StartNext, plan.intent)
    }

    @Test
    fun `无待执行项时提交完成退出`() {
        val plan = plan()

        assertTrue(plan.enabled)
        assertEquals(KiteInstallPlanActionIntent.Finish, plan.intent)
    }

    private fun plan(
        hasRunningStep: Boolean = false,
        hasUninstallingStep: Boolean = false,
        hasPending: Boolean = false,
        hasFailure: Boolean = false
    ): KiteInstallPlanActionPlan = KiteInstallPlanActionCoordinator.plan(
        hasRunningStep,
        hasUninstallingStep,
        hasPending,
        hasFailure
    )
}
