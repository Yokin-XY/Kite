package com.kite.app.feature.runsurface

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMediaPolicyTest {
    @Test
    fun `two mebibyte text boundary is accepted`() {
        val bytes = ByteArray(AgentMediaPolicy.MAX_TEXT_BYTES) { 'a'.code.toByte() }
        var opened = false

        val text = AgentMediaPolicy.readInlineText(bytes.size.toLong()) {
            opened = true
            ByteArrayInputStream(bytes)
        }

        assertTrue(opened)
        assertEquals(AgentMediaPolicy.MAX_TEXT_BYTES, text.length)
        assertEquals('a', text.first())
        assertEquals('a', text.last())
    }

    @Test
    fun `declared text one byte over limit is rejected before opening stream`() {
        var opened = false

        assertThrows(IllegalArgumentException::class.java) {
            AgentMediaPolicy.readInlineText(AgentMediaPolicy.MAX_TEXT_BYTES + 1L) {
                opened = true
                ByteArrayInputStream(byteArrayOf())
            }
        }

        assertFalse(opened)
    }

    @Test
    fun `unknown size text stops after limit plus one byte`() {
        val input = GeneratedCountingInputStream(AgentMediaPolicy.MAX_TEXT_BYTES + 8_192)

        assertThrows(IllegalArgumentException::class.java) {
            AgentMediaPolicy.readInlineText(null) { input }
        }

        assertEquals(AgentMediaPolicy.MAX_TEXT_BYTES + 1, input.bytesRead)
        assertTrue(input.closed)
    }

    @Test
    fun `small utf8 text keeps existing inline content`() {
        val expected = "普通文本\nhello"
        val bytes = expected.toByteArray(Charsets.UTF_8)

        val actual = AgentMediaPolicy.readInlineText(bytes.size.toLong()) {
            ByteArrayInputStream(bytes)
        }

        assertEquals(expected, actual)
    }

    private class GeneratedCountingInputStream(
        private val totalBytes: Int
    ) : InputStream() {
        var bytesRead: Int = 0
            private set
        var closed: Boolean = false
            private set

        override fun read(): Int {
            if (bytesRead >= totalBytes) return -1
            bytesRead++
            return 'x'.code
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= totalBytes) return -1
            val count = minOf(length, totalBytes - bytesRead)
            buffer.fill('x'.code.toByte(), offset, offset + count)
            bytesRead += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
