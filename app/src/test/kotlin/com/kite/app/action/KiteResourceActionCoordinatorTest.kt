package com.kite.app.action

import org.junit.Assert.assertEquals
import org.junit.Test

class KiteResourceActionCoordinatorTest {
    @Test
    fun `获取动作有现存计划时改为恢复向导`() {
        assertEquals(
            KiteResourceActionIntent.ReopenInstall,
            KiteResourceActionCoordinator.primaryIntent("获取", reopenInstall = true)
        )
    }

    @Test
    fun `资源投影标签归一化为稳定意图`() {
        val expected = mapOf(
            "获取" to KiteResourceActionIntent.Install,
            "重新获取" to KiteResourceActionIntent.Install,
            "获取中" to KiteResourceActionIntent.ReopenInstall,
            "打开" to KiteResourceActionIntent.Open,
            "运行中" to KiteResourceActionIntent.Open,
            "卸载" to KiteResourceActionIntent.Uninstall,
            "卸载中" to KiteResourceActionIntent.BusyStatus,
            "未知" to KiteResourceActionIntent.Unsupported
        )

        expected.forEach { (label, intent) ->
            assertEquals(intent, KiteResourceActionCoordinator.primaryIntent(label, reopenInstall = false))
        }
    }
}
