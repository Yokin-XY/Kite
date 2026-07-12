package com.kite.app.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteCardRunUiProjectorTest {
    @Test
    fun `运行与停止状态给出确定主动作`() {
        val running = KiteCardRunUiProjector.project(CardRunStatus.Running)
        val stopping = KiteCardRunUiProjector.project(CardRunStatus.Stopping)

        assertEquals(KiteRunPrimaryAction.Stop, running.primaryAction)
        assertEquals("停止", running.primaryActionLabel)
        assertEquals(KiteRunPrimaryAction.Busy, stopping.primaryAction)
        assertFalse(stopping.primaryActionEnabled)
    }

    @Test
    fun `失败状态在所有页面统一为危险重试`() {
        val failed = KiteCardRunUiProjector.project(CardRunStatus.Failed)

        assertEquals("失败", failed.badgeLabel)
        assertEquals(KiteRunUiTone.Danger, failed.tone)
        assertEquals(KiteRunPrimaryAction.Retry, failed.primaryAction)
        assertTrue(failed.problem)
    }

    @Test
    fun `运行环境阻塞只改变动作承诺不改变事实色调`() {
        val blocked = KiteCardRunUiProjector.project(CardRunStatus.Stopped, runtimeBlocked = true)

        assertEquals(KiteRunPrimaryAction.Blocked, blocked.primaryAction)
        assertEquals(KiteRunUiTone.Neutral, blocked.tone)
        assertFalse(blocked.live)
    }
}
