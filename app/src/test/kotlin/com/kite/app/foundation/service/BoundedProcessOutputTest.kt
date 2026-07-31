package com.kite.app.foundation.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedProcessOutputTest {
    @Test
    fun `output within limit is retained exactly`() {
        val output = BoundedProcessOutput(maxChars = 8)

        output.append("hello".toCharArray(), 5)

        assertEquals("hello", output.snapshot().text)
        assertFalse(output.snapshot().truncated)
    }

    @Test
    fun `overflow is drained logically but retained prefix stays bounded`() {
        val output = BoundedProcessOutput(maxChars = 5)

        output.append("abc".toCharArray(), 3)
        output.append("defgh".toCharArray(), 5)

        assertEquals("abcde", output.snapshot().text)
        assertTrue(output.snapshot().truncated)
    }
}
