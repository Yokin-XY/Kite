package com.kite.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityResourceStateTargetTest {
    @Test
    fun `最新资源事件不能排除其他可见资源和当前详情`() {
        val targets = resourceStatePatchTargetIds(
            visibleResourceIds = listOf("resource.visible.a", "resource.visible.b"),
            preferredResourceIds = listOf("resource.event.c"),
            detailResourceId = "resource.detail.d"
        )

        assertEquals(
            setOf(
                "resource.visible.a",
                "resource.visible.b",
                "resource.event.c",
                "resource.detail.d"
            ),
            targets
        )
    }

    @Test
    fun `资源刷新目标必须清理空值并去重`() {
        val targets = resourceStatePatchTargetIds(
            visibleResourceIds = listOf("resource.same", ""),
            preferredResourceIds = listOf("resource.same", "   "),
            detailResourceId = "resource.same"
        )

        assertEquals(setOf("resource.same"), targets)
    }
}
