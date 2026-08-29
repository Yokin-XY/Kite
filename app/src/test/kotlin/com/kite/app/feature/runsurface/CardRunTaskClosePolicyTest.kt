package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Test

class CardRunTaskClosePolicyTest {
    @Test
    fun `安装向导返回交给状态拥有者解析是否尚未开始`() {
        val action = CardRunTaskNavigationPolicy.decide(
            state(CardRunState.OWNER_KIND_INSTALL_WIZARD, CardRunSurface.InstallWizard)
        )

        assertEquals(CardRunTaskNavigationAction.ResolveInstallWizardBack, action)
    }

    @Test
    fun `普通运行导航同样只隐藏任务并保留实例`() {
        val action = CardRunTaskNavigationPolicy.decide(
            state(CardRunState.OWNER_KIND_CARD, CardRunSurface.Terminal)
        )

        assertEquals(CardRunTaskNavigationAction.HideTask, action)
    }

    @Test
    fun `没有有效实例的空窗口可以直接关闭`() {
        assertEquals(
            CardRunTaskNavigationAction.CloseTask,
            CardRunTaskNavigationPolicy.decide(null),
        )
    }

    private fun state(
        ownerKind: String,
        surface: CardRunSurface,
    ) = CardRunState(
        instanceId = "instance-a",
        recipeId = "recipe-a",
        recipeName = "测试",
        ownerKind = ownerKind,
        status = CardRunStatus.Opened,
        surface = surface,
    )
}
