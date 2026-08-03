package com.kite.app.foundation.runtime

import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeDnsFilePublisherTest {
    @Test
    fun `an already opened resolver handle observes later dns updates`() {
        val directory = Files.createTempDirectory("kite-runtime-dns").toFile()
        val resolver = directory.resolve("resolv.conf")

        try {
            assertTrue(RuntimeDnsFilePublisher.publish(resolver, listOf("192.0.2.1")))
            RandomAccessFile(resolver, "r").use { openedBeforeVpnChange ->
                assertTrue(RuntimeDnsFilePublisher.publish(resolver, listOf("198.51.100.2")))

                openedBeforeVpnChange.seek(0L)
                val current = ByteArray(openedBeforeVpnChange.length().toInt())
                openedBeforeVpnChange.readFully(current)

                val text = String(current, StandardCharsets.UTF_8)
                assertTrue(text.contains("nameserver 198.51.100.2"))
                assertFalse(text.contains("nameserver 192.0.2.1"))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `publishing unchanged dns leaves resolver untouched`() {
        val directory = Files.createTempDirectory("kite-runtime-dns").toFile()
        val resolver = directory.resolve("resolv.conf")

        try {
            assertTrue(RuntimeDnsFilePublisher.publish(resolver, listOf("203.0.113.3")))
            assertFalse(RuntimeDnsFilePublisher.publish(resolver, listOf("203.0.113.3")))
        } finally {
            directory.deleteRecursively()
        }
    }
}
