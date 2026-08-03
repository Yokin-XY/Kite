package com.kite.app.application.resources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceActionMessagePolicyTest {
    @Test
    fun `资源状态页隐藏普通状态消息`() {
        assertFalse(
            ResourceActionMessagePolicy.shouldShow(
                presentation = ResourceActionMessagePresentation.StatusAware,
                onResourceStatusScreen = true,
            )
        )
    }

    @Test
    fun `资源状态页仍显示用户操作的明确结果`() {
        assertTrue(
            ResourceActionMessagePolicy.shouldShow(
                presentation = ResourceActionMessagePresentation.ExplicitResult,
                onResourceStatusScreen = true,
            )
        )
    }
}
