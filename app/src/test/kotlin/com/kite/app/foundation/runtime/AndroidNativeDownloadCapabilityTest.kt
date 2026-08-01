package com.kite.app.foundation.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNativeDownloadCapabilityTest {
    @Test
    fun `provider accepts only closed https request inside declared destination root`() {
        val root = Files.createTempDirectory("kite-native-download-provider").toFile()
        val context = AndroidNativeCapabilityContext(
            listOf(NativeCapabilityDestinationRoot("/workspace", root)),
        )
        val ready = AndroidNativeDownloadCapabilityProvider.prepare(
            context,
            request(destination = "/workspace/cache/payload.bin"),
        ) as RuntimeProviderDecision.Ready

        assertEquals(RuntimeProviderKind.ANDROID_NATIVE, ready.provider)
        assertEquals(File(root, "cache/payload.bin").canonicalFile, ready.plan.destination)
        assertTrue(ready.plan.temporaryFile.parentFile == ready.plan.destination.parentFile)
        assertEquals("native_download_sha256_ready", ready.reason)

        assertBlocked(context, request(url = "http://example.test/file"), "native_download_url_invalid")
        assertBlocked(context, request(destination = "/workspace/../outside"), "native_download_destination_invalid")
        assertBlocked(
            context,
            request(extra = mapOf("trustedPackage" to "yes")),
            "native_download_parameter_unknown",
        )
        assertBlocked(
            context,
            request(expectedSha256 = "trusted"),
            "native_download_sha256_invalid",
        )
    }

    @Test
    fun `verified bytes publish once and preserve streaming digest`() {
        val root = Files.createTempDirectory("kite-native-download-success").toFile()
        val bytes = "native-download-payload".toByteArray()
        val plan = plan(root, expectedSha256 = bytes.sha256())
        val progress = mutableListOf<Long>()
        val result = AndroidNativeDownloadExecutor(
            queueFactory(Response(200, bytes)),
        ).execute(plan, progress = NativeDownloadProgressListener { written, _ -> progress += written })

        result as NativeDownloadExecutionResult.Success
        assertEquals(bytes.toList(), result.destination.readBytes().toList())
        assertEquals(bytes.sha256(), result.actualSha256)
        assertEquals(bytes.size.toLong(), result.bytesWritten)
        assertEquals(1, result.attempts)
        assertEquals(bytes.size.toLong(), progress.last())
        assertFalse(plan.temporaryFile.exists())
    }

    @Test
    fun `digest mismatch never replaces existing target and cleans temporary file`() {
        val root = Files.createTempDirectory("kite-native-download-mismatch").toFile()
        val plan = plan(root, expectedSha256 = ByteArray(32).sha256(), replaceExisting = true)
        plan.destination.writeText("old")
        val result = AndroidNativeDownloadExecutor(
            queueFactory(Response(200, "new".toByteArray())),
        ).execute(plan)

        assertEquals(
            NativeDownloadExecutionResult.Failure("native_download_sha256_mismatch", 1),
            result,
        )
        assertEquals("old", plan.destination.readText())
        assertFalse(plan.temporaryFile.exists())
    }

    @Test
    fun `retry restarts from zero and publishes only successful attempt`() {
        val root = Files.createTempDirectory("kite-native-download-retry").toFile()
        val bytes = "retry-success".toByteArray()
        val plan = plan(root, expectedSha256 = bytes.sha256(), maximumAttempts = 2)
        val attempts = mutableListOf<Int>()
        val factory = NativeDownloadConnectionFactory { _, _, _ ->
            attempts += attempts.size + 1
            if (attempts.size == 1) throw IOException("network reset")
            Response(200, bytes)
        }
        val result = AndroidNativeDownloadExecutor(factory).execute(plan)

        result as NativeDownloadExecutionResult.Success
        assertEquals(2, result.attempts)
        assertEquals(listOf(1, 2), attempts)
        assertEquals(bytes.toList(), plan.destination.readBytes().toList())
        assertFalse(plan.temporaryFile.exists())
    }

    @Test
    fun `midstream network interruption discards partial bytes before retry`() {
        val root = Files.createTempDirectory("kite-native-download-midstream").toFile()
        val bytes = "complete-after-interruption".toByteArray()
        val plan = plan(root, expectedSha256 = bytes.sha256(), maximumAttempts = 2)
        val result = AndroidNativeDownloadExecutor(
            queueFactory(InterruptedResponse(), Response(200, bytes)),
        ).execute(plan)

        result as NativeDownloadExecutionResult.Success
        assertEquals(2, result.attempts)
        assertEquals(bytes.toList(), plan.destination.readBytes().toList())
        assertEquals(bytes.sha256(), result.actualSha256)
        assertFalse(plan.temporaryFile.exists())
    }

    @Test
    fun `cancellation and size limit leave no target or temporary file`() {
        val root = Files.createTempDirectory("kite-native-download-cancel").toFile()
        val cancelled = NativeDownloadCancellationSignal()
        val payload = ByteArray(128 * 1024) { 7 }
        val cancelPlan = plan(root, maximumBytes = payload.size.toLong())
        val cancelResult = AndroidNativeDownloadExecutor(
            queueFactory(Response(200, payload, chunkSize = 1024)),
        ).execute(
            cancelPlan,
            cancellation = cancelled,
            progress = NativeDownloadProgressListener { _, _ -> cancelled.cancel() },
        )
        cancelResult as NativeDownloadExecutionResult.Cancelled
        assertTrue(cancelResult.bytesWritten > 0)
        assertFalse(cancelPlan.destination.exists())
        assertFalse(cancelPlan.temporaryFile.exists())

        val sizePlan = plan(root, maximumBytes = 3, name = "size.bin")
        val sizeResult = AndroidNativeDownloadExecutor(
            queueFactory(Response(200, "too-large".toByteArray())),
        ).execute(sizePlan)
        assertEquals(NativeDownloadExecutionResult.Failure("native_download_size_limit", 1), sizeResult)
        assertFalse(sizePlan.destination.exists())
        assertFalse(sizePlan.temporaryFile.exists())
    }

    @Test
    fun `redirect downgrade and unexpected partial response fail closed`() {
        val root = Files.createTempDirectory("kite-native-download-redirect").toFile()
        val downgrade = AndroidNativeDownloadExecutor(
            queueFactory(Response(302, ByteArray(0), headers = mapOf("Location" to "http://example.test/file"))),
        ).execute(plan(root, name = "downgrade.bin"))
        assertEquals(NativeDownloadExecutionResult.Failure("native_download_redirect_invalid", 1), downgrade)

        val partial = AndroidNativeDownloadExecutor(
            queueFactory(Response(206, "partial".toByteArray())),
        ).execute(plan(root, name = "partial.bin"))
        assertEquals(NativeDownloadExecutionResult.Failure("native_download_unexpected_partial", 1), partial)
    }

    @Test
    fun `declared content larger than usable space fails before creating target`() {
        val root = Files.createTempDirectory("kite-native-download-space").toFile()
        val plan = plan(root, maximumBytes = Long.MAX_VALUE, name = "space.bin")
        val response = object : NativeDownloadConnection {
            override val responseCode: Int = 200
            override val contentLength: Long = Long.MAX_VALUE - 1
            override fun header(name: String): String? = null
            override fun inputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
            override fun close() = Unit
        }

        val result = AndroidNativeDownloadExecutor(queueFactory(response)).execute(plan)

        assertEquals(NativeDownloadExecutionResult.Failure("native_download_insufficient_space", 1), result)
        assertFalse(plan.destination.exists())
        assertFalse(plan.temporaryFile.exists())
    }

    @Test
    fun `large payload remains streaming and publishes the complete digest`() {
        val root = Files.createTempDirectory("kite-native-download-large").toFile()
        val size = 32L * 1024L * 1024L
        val plan = plan(root, expectedSha256 = repeatedByteSha256(size, 19), maximumBytes = size)
        val result = AndroidNativeDownloadExecutor(
            queueFactory(GeneratedResponse(size, 19)),
        ).execute(plan)

        result as NativeDownloadExecutionResult.Success
        assertEquals(size, result.bytesWritten)
        assertEquals(size, plan.destination.length())
        assertEquals(repeatedByteSha256(size, 19), result.actualSha256)
        assertFalse(plan.temporaryFile.exists())
    }

    private fun assertBlocked(
        context: AndroidNativeCapabilityContext,
        request: RuntimeExecutionRequest,
        reason: String,
    ) {
        assertEquals(
            RuntimeProviderDecision.Blocked(RuntimeProviderKind.ANDROID_NATIVE, reason),
            AndroidNativeDownloadCapabilityProvider.prepare(context, request),
        )
    }

    private fun request(
        url: String = "https://example.test/file",
        destination: String = "/workspace/payload.bin",
        expectedSha256: String = "",
        extra: Map<String, String> = emptyMap(),
    ) = RuntimeExecutionRequest(
        payload = RuntimeExecutionPayload.NativeCapability(
            AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID,
            mapOf(
                AndroidNativeDownloadCapabilityProvider.PARAM_URL to url,
                AndroidNativeDownloadCapabilityProvider.PARAM_DESTINATION to destination,
                AndroidNativeDownloadCapabilityProvider.PARAM_EXPECTED_SHA256 to expectedSha256,
                AndroidNativeDownloadCapabilityProvider.PARAM_MAX_BYTES to "1048576",
            ) + extra,
        ),
        requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
    )

    private fun plan(
        root: File,
        expectedSha256: String? = null,
        maximumBytes: Long = 1024 * 1024,
        maximumAttempts: Int = 1,
        replaceExisting: Boolean = false,
        name: String = "payload.bin",
    ) = AndroidNativeDownloadPlan(
        source = java.net.URI("https://example.test/file"),
        destination = File(root, name),
        temporaryFile = File(root, ".$name.part"),
        expectedSha256 = expectedSha256,
        maximumBytes = maximumBytes,
        connectTimeoutMs = 1_000,
        readTimeoutMs = 1_000,
        maximumAttempts = maximumAttempts,
        retryDelayMs = 0,
        replaceExisting = replaceExisting,
    )

    private fun queueFactory(vararg responses: NativeDownloadConnection): NativeDownloadConnectionFactory {
        val queue = ArrayDeque(responses.toList())
        return NativeDownloadConnectionFactory { _: URL, _: Int, _: Int -> queue.removeFirst() }
    }

    private class Response(
        override val responseCode: Int,
        private val bytes: ByteArray,
        private val headers: Map<String, String> = emptyMap(),
        private val chunkSize: Int = Int.MAX_VALUE,
    ) : NativeDownloadConnection {
        override val contentLength: Long = bytes.size.toLong()
        override fun header(name: String): String? = headers[name]
        override fun inputStream(): InputStream = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, chunkSize))
        }
        override fun close() = Unit
    }

    private class GeneratedResponse(
        private val size: Long,
        private val value: Int,
    ) : NativeDownloadConnection {
        override val responseCode: Int = 200
        override val contentLength: Long = size
        override fun header(name: String): String? = null
        override fun inputStream(): InputStream = object : InputStream() {
            private var remaining = size

            override fun read(): Int = if (remaining-- > 0L) value else -1

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0L) return -1
                val count = minOf(length.toLong(), remaining).toInt()
                buffer.fill(value.toByte(), offset, offset + count)
                remaining -= count
                return count
            }
        }
        override fun close() = Unit
    }

    private class InterruptedResponse : NativeDownloadConnection {
        override val responseCode: Int = 200
        override val contentLength: Long = -1L
        override fun header(name: String): String? = null
        override fun inputStream(): InputStream = object : InputStream() {
            private var emitted = false

            override fun read(): Int = error("single_byte_read_not_supported")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (emitted) throw IOException("connection reset midstream")
                emitted = true
                val count = minOf(length, 4096)
                buffer.fill(23.toByte(), offset, offset + count)
                return count
            }
        }
        override fun close() = Unit
    }

    private fun repeatedByteSha256(size: Long, value: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024) { value.toByte() }
        var remaining = size
        while (remaining > 0L) {
            val count = minOf(buffer.size.toLong(), remaining).toInt()
            digest.update(buffer, 0, count)
            remaining -= count
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
