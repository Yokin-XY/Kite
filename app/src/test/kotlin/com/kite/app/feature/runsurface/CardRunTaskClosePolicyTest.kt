package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRunTaskClosePolicyTest {
    @Test
    fun `完成计划退出时移除安装向导临时实例`() {
        val state = state(
            ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
            surface = CardRunSurface.InstallWizard,
        )

        assertTrue(
            CardRunTaskClosePolicy.shouldRemoveRunState(
                state = state,
                reason = CardRunTaskCloseReason.FinishCompleted,
                hasActiveInstallPlan = false,
                hasActiveChildRun = false,
            )
        )
    }

    @Test
    fun `安装进行中返回只退出显示面并保留向导根`() {
        val state = state(
            ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
            surface = CardRunSurface.InstallWizard,
        )

        assertFalse(
            CardRunTaskClosePolicy.shouldRemoveRunState(
                state = state,
                reason = CardRunTaskCloseReason.DismissSurface,
                hasActiveInstallPlan = true,
                hasActiveChildRun = true,
            )
        )
    }

    @Test
    fun `计划事实暂时清空但子运行仍活动时保留向导根`() {
        val state = state(
            ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
            surface = CardRunSurface.InstallWizard,
        )

        assertFalse(
            CardRunTaskClosePolicy.shouldRemoveRunState(
                state = state,
                reason = CardRunTaskCloseReason.DismissSurface,
                hasActiveInstallPlan = false,
                hasActiveChildRun = true,
            )
        )
    }

    @Test
    fun `确认停止整个实例后允许移除向导根`() {
        val state = state(
            ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
            surface = CardRunSurface.InstallWizard,
        )

        assertTrue(
            CardRunTaskClosePolicy.shouldRemoveRunState(
                state = state,
                reason = CardRunTaskCloseReason.StopConfirmed,
                hasActiveInstallPlan = true,
                hasActiveChildRun = true,
            )
        )
    }

    @Test
    fun `普通卡片关闭窗口时保留运行事实`() {
        val state = state(
            ownerKind = CardRunState.OWNER_KIND_CARD,
            surface = CardRunSurface.Terminal,
        )

        assertFalse(
            CardRunTaskClosePolicy.shouldRemoveRunState(
                state = state,
                reason = CardRunTaskCloseReason.StopConfirmed,
                hasActiveInstallPlan = false,
                hasActiveChildRun = false,
            )
        )
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
