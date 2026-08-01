package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRunTaskClosePolicyTest {
    @Test
    fun `安装向导普通导航只隐藏任务而不进入关闭策略`() {
        val action = CardRunTaskNavigationPolicy.decide(
            state(CardRunState.OWNER_KIND_INSTALL_WIZARD, CardRunSurface.InstallWizard)
        )

        assertEquals(CardRunTaskNavigationAction.HideTask, action)
    }

    @Test
    fun `普通运行导航沿用关闭任务窗口语义`() {
        val action = CardRunTaskNavigationPolicy.decide(
            state(CardRunState.OWNER_KIND_CARD, CardRunSurface.Terminal)
        )

        assertEquals(CardRunTaskNavigationAction.CloseTask, action)
    }

    @Test
    fun `完成计划退出时移除安装向导临时实例`() {
        val decision = decide(
            reason = CardRunTaskCloseReason.FinishCompleted,
            hasInstallPlan = false,
            hasRunningInstallPlan = false,
            hasActiveChildRun = false,
        )

        assertTrue(decision.removeRunState)
        assertFalse(decision.clearInstallPlan)
    }

    @Test
    fun `显式移除未开始的向导任务时清理计划与向导根`() {
        val decision = decide(
            reason = CardRunTaskCloseReason.DismissSurface,
            hasInstallPlan = true,
            hasRunningInstallPlan = false,
            hasActiveChildRun = false,
        )

        assertTrue(decision.removeRunState)
        assertTrue(decision.clearInstallPlan)
    }

    @Test
    fun `安装实际运行中退出只隐藏显示面`() {
        val decision = decide(
            reason = CardRunTaskCloseReason.DismissSurface,
            hasInstallPlan = true,
            hasRunningInstallPlan = true,
            hasActiveChildRun = false,
        )

        assertFalse(decision.removeRunState)
        assertFalse(decision.clearInstallPlan)
    }

    @Test
    fun `计划事实暂时没有运行项但子运行仍活动时保留向导根`() {
        val decision = decide(
            reason = CardRunTaskCloseReason.DismissSurface,
            hasInstallPlan = true,
            hasRunningInstallPlan = false,
            hasActiveChildRun = true,
        )

        assertFalse(decision.removeRunState)
        assertFalse(decision.clearInstallPlan)
    }

    @Test
    fun `确认停止整个实例后清理计划与向导根`() {
        val decision = decide(
            reason = CardRunTaskCloseReason.StopConfirmed,
            hasInstallPlan = true,
            hasRunningInstallPlan = true,
            hasActiveChildRun = true,
        )

        assertTrue(decision.removeRunState)
        assertTrue(decision.clearInstallPlan)
    }

    @Test
    fun `普通卡片关闭窗口时保留运行事实`() {
        val state = state(
            ownerKind = CardRunState.OWNER_KIND_CARD,
            surface = CardRunSurface.Terminal,
        )
        val decision = CardRunTaskClosePolicy.decide(
            state = state,
            reason = CardRunTaskCloseReason.StopConfirmed,
            hasInstallPlan = false,
            hasRunningInstallPlan = false,
            hasActiveChildRun = false,
        )

        assertFalse(decision.removeRunState)
        assertFalse(decision.clearInstallPlan)
    }

    @Test
    fun `运行中和停止待确认子实例均视为活动`() {
        assertTrue(CardRunTaskClosePolicy.isActiveChild(state(CardRunState.OWNER_KIND_RESOURCE, CardRunSurface.Report)))
        assertTrue(
            CardRunTaskClosePolicy.isActiveChild(
                state(CardRunState.OWNER_KIND_RESOURCE, CardRunSurface.Report, CardRunStatus.CleanupPending)
            )
        )
        assertFalse(
            CardRunTaskClosePolicy.isActiveChild(
                state(CardRunState.OWNER_KIND_RESOURCE, CardRunSurface.Report, CardRunStatus.Completed)
            )
        )
    }

    private fun decide(
        reason: CardRunTaskCloseReason,
        hasInstallPlan: Boolean,
        hasRunningInstallPlan: Boolean,
        hasActiveChildRun: Boolean,
    ) = CardRunTaskClosePolicy.decide(
        state = state(CardRunState.OWNER_KIND_INSTALL_WIZARD, CardRunSurface.InstallWizard),
        reason = reason,
        hasInstallPlan = hasInstallPlan,
        hasRunningInstallPlan = hasRunningInstallPlan,
        hasActiveChildRun = hasActiveChildRun,
    )

    private fun state(
        ownerKind: String,
        surface: CardRunSurface,
        status: CardRunStatus = CardRunStatus.Opened,
    ) = CardRunState(
        instanceId = "instance-a",
        recipeId = "recipe-a",
        recipeName = "测试",
        ownerKind = ownerKind,
        status = status,
        surface = surface,
    )
}
