package com.kite.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRunTaskCloserTest {
    @Test
    fun `旧代次不能关闭同名新代次窗口`() {
        var closed = false
        CardRunTaskCloser.register("instance", 2L) { closed = true }

        assertFalse(CardRunTaskCloser.close("instance", 1L))
        assertFalse(closed)
        assertTrue(CardRunTaskCloser.close("instance", 2L))
        assertTrue(closed)

        CardRunTaskCloser.unregister("instance", 2L)
    }

    @Test
    fun `注销旧代次不会移除新代次关闭回调`() {
        var closed = false
        CardRunTaskCloser.register("same", 3L) { error("不应调用旧代次") }
        CardRunTaskCloser.register("same", 4L) { closed = true }

        CardRunTaskCloser.unregister("same", 3L)

        assertTrue(CardRunTaskCloser.close("same", 4L))
        assertTrue(closed)
        CardRunTaskCloser.unregister("same", 4L)
    }
}
