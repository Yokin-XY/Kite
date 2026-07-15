package com.kite.app.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteLaunchConfigTest {
    @Test
    fun `结束通知策略可以随卡片 JSON 往返`() {
        val config = KiteLaunchConfig(
            openInstance = false,
            keepFinishedNotification = true
        )

        assertEquals(config, KiteLaunchConfig.fromJson(config.toJson()))
        assertFalse(config.isDefault())
    }

    @Test
    fun `旧卡片默认不保留结束通知`() {
        val config = KiteLaunchConfig.fromJson(null)

        assertTrue(config.openInstance)
        assertFalse(config.keepFinishedNotification)
        assertTrue(config.isDefault())
    }
}
