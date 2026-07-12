package com.kite.app.foundation.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionEndPolicyTest {
    @Test
    fun `结束当前普通终端时可以选择已有会话作为返回目标`() {
        assertTrue(
            TerminalSessionEndPolicy.shouldSelectManagedFallback(
                targetIsActive = true,
                targetIsManaged = true
            )
        )
    }

    @Test
    fun `结束卡片内嵌终端时不能唤醒其他终端`() {
        assertFalse(
            TerminalSessionEndPolicy.shouldSelectManagedFallback(
                targetIsActive = true,
                targetIsManaged = false
            )
        )
    }

    @Test
    fun `结束非当前终端时不切换显示会话`() {
        assertFalse(
            TerminalSessionEndPolicy.shouldSelectManagedFallback(
                targetIsActive = false,
                targetIsManaged = true
            )
        )
    }
}
