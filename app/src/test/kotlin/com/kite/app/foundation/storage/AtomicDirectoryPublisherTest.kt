package com.kite.app.foundation.storage

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicDirectoryPublisherTest {
    @Test
    fun `concurrent consumers all observe one complete artifact`() {
        val root = Files.createTempDirectory("kite-artifact-publisher").toFile()
        val destination = File(root, "shared-pack")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(6)
        val expectedPayload = buildString {
            repeat(2_000) { index -> append("payload-$index\n") }
        }
        val complete: (File) -> Boolean = { candidate ->
            File(candidate, "manifest.txt").readTextOrNull() == "ready" &&
                File(candidate, "payload.txt").readTextOrNull() == expectedPayload
        }

        try {
            val results = (1..12).map {
                executor.submit<File> {
                    start.await(2L, TimeUnit.SECONDS)
                    AtomicDirectoryPublisher.publish(destination, complete) { pending ->
                        pending.mkdirs()
                        File(pending, "payload.txt").bufferedWriter().use { writer ->
                            expectedPayload.chunked(127).forEach { chunk ->
                                writer.write(chunk)
                                Thread.yield()
                            }
                        }
                        File(pending, "manifest.txt").writeText("ready")
                    }
                }
            }
            start.countDown()

            assertTrue(results.all { future -> complete(future.get(10L, TimeUnit.SECONDS)) })
            assertTrue(complete(destination))
            assertFalse(
                root.listFiles().orEmpty().any { file ->
                    file.name.startsWith(".shared-pack.kite-pending-") ||
                        file.name == ".shared-pack.kite-previous"
                },
            )
            assertEquals(setOf("manifest.txt", "payload.txt"), destination.list().orEmpty().toSet())
        } finally {
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }

    private fun File.readTextOrNull(): String? = runCatching {
        takeIf(File::isFile)?.readText()
    }.getOrNull()
}
