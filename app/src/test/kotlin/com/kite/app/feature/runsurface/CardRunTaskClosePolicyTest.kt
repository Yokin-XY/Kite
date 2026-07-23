package com.kite.app.feature.runsurface

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRunTaskClosePolicyTest {
    @Test
    fun `明确退出安装向导时移除临时运行实例`() {
        val state = state(
            ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
            surface = CardRunSurface.InstallWizard,
        )

        assertTrue(CardRunTaskClosePolicy.shouldRemoveRunState(state))
    }

    @Test
    fun `普通卡片关闭窗口时保留运行事实`() {
        val state = state(
            ownerKind = CardRunState.OWNER_KIND_CARD,
            surface = CardRunSurface.Terminal,
        )

        assertFalse(CardRunTaskClosePolicy.shouldRemoveRunState(state))
    }

    private fun state(ownerKind: String, surface: CardRunSurface) = CardRunState(
        instanceId = "instance-a",
        recipeId = "recipe-a",
        recipeName = "测试",
        ownerKind = ownerKind,
        status = CardRunStatus.Opened,
        surface = surface,
    )
}
