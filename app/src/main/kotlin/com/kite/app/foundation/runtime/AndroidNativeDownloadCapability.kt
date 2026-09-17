package com.kite.app.foundation.runtime

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

internal data class NativeCapabilityDestinationRoot(
    val containerPath: String,
    val directory: File,
) {
    init {
        require(containerPath.startsWith('/')) { "native_destination_root_not_absolute" }
        require(containerPath != "/") { "native_destination_root_too_broad" }
    }
}

internal data class AndroidNativeCapabilityContext(
    val destinationRoots: List<NativeCapabilityDestinationRoot>,
) {
    init {
        require(destinationRoots.isNotEmpty()) { "native_destination_roots_missing" }
    }
}

internal data class AndroidNativeDownloadPlan(
    val source: URI,
    val destination: File,
    val temporaryFile: File,
    val expectedSha256: String?,
    val maximumBytes: Long,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maximumAttempts: Int,
    val retryDelayMs: Long,
    val replaceExisting: Boolean,
    val fallbackSources: List<URI> = emptyList(),
)

/**
 * 只把封闭的 HTTPS 下载请求编译成原生计划；不联网、不写文件、不拥有运行状态。
 */
internal object AndroidNativeDownloadCapabilityProvider :
    RuntimeExecutionProvider<AndroidNativeCapabilityContext, AndroidNativeDownloadPlan> {
    override val kind: RuntimeProviderKind = RuntimeProviderKind.ANDROID_NATIVE

    override fun prepare(
        context: AndroidNativeCapabilityContext,
        request: RuntimeExecutionRequest,
    ): RuntimeProviderDecision<AndroidNativeDownloadPlan> {
        val payload = request.payload as? RuntimeExecutionPayload.NativeCapability
            ?: return unsupported("native_capability_payload_required")
        if (payload.capabilityId != CAPABILITY_ID) {
            return unsupported("native_capability_not_supported")
        }
        if (request.workingDirectory != null || request.environment.isNotEmpty() ||
            request.guarantees.isNotEmpty() || request.guaranteeEvidence.isNotEmpty()
        ) {
            return blocked("native_download_ignored_request_fields")
        }
        if (request.requirements.any { it != RuntimeExecutionRequirement.ANDROID_NATIVE }) {
            return blocked("native_download_requirement_conflict")
        }
        val parameters = payload.parameters.mapValues { (_, value) -> value.trim() }
        if (parameters.keys.any { it !in PARAMETERS }) {
            return blocked("native_download_parameter_unknown")
        }
        val sources = parseSources(parameters)
            ?: return blocked("native_download_url_invalid")
        val destination = parameters[PARAM_DESTINATION]
            ?.let { resolveDestination(context.destinationRoots, it) }
            ?: return blocked("native_download_destination_invalid")
        val expectedSha256 = parameters[PARAM_EXPECTED_SHA256]
            ?.takeIf(String::isNotBlank)
            ?.lowercase()
            ?.takeIf(SHA256::matches)
            ?: if (parameters[PARAM_EXPECTED_SHA256].isNullOrBlank()) null else {
                return blocked("native_download_sha256_invalid")
            }
        if (sources.size > 1 && expectedSha256 == null) {
            return blocked("native_download_mirrors_require_sha256")
        }
        val maximumBytes = parameters[PARAM_MAX_BYTES]
            ?.toLongOrNull()
            ?.takeIf { it in 1..MAXIMUM_BYTES }
            ?: return blocked("native_download_max_bytes_invalid")
        val connectTimeoutMs = parameters.intValue(
            PARAM_CONNECT_TIMEOUT_MS,
            DEFAULT_CONNECT_TIMEOUT_MS,
            1_000..120_000,
        ) ?: return blocked("native_download_connect_timeout_invalid")
        val readTimeoutMs = parameters.intValue(
            PARAM_READ_TIMEOUT_MS,
            DEFAULT_READ_TIMEOUT_MS,
            1_000..300_000,
        ) ?: return blocked("native_download_read_timeout_invalid")
        val maximumAttempts = parameters.intValue(
            PARAM_MAX_ATTEMPTS,
            DEFAULT_MAXIMUM_ATTEMPTS,
            1..4,
        ) ?: return blocked("native_download_attempts_invalid")
        val retryDelayMs = parameters[PARAM_RETRY_DELAY_MS]
            ?.takeIf(String::isNotBlank)
            ?.toLongOrNull()
            ?.takeIf { it in 0..30_000 }
            ?: if (parameters[PARAM_RETRY_DELAY_MS].isNullOrBlank()) DEFAULT_RETRY_DELAY_MS else {
                return blocked("native_download_retry_delay_invalid")
            }
        val replaceExisting = when (parameters[PARAM_REPLACE_EXISTING]?.lowercase().orEmpty()) {
            "", "false" -> false
            "true" -> true
            else -> return blocked("native_download_replace_existing_invalid")
        }
        val temporaryFile = File(
            checkNotNull(destination.parentFile),
            ".${destination.name}.kite-download-${UUID.randomUUID().toString().replace("-", "")}.part",
        )
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = AndroidNativeDownloadPlan(
                source = sources.first(),
                destination = destination,
                temporaryFile = temporaryFile,
                expectedSha256 = expectedSha256,
                maximumBytes = maximumBytes,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs,
                maximumAttempts = maximumAttempts,
                retryDelayMs = retryDelayMs,
                replaceExisting = replaceExisting,
                fallbackSources = sources.drop(1),
            ),
            reason = "native_download_sha256_ready",
        )
    }

    private fun parseHttpsUri(value: String): URI? = runCatching { URI(value) }.getOrNull()
        ?.takeIf { uri ->
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null
        }

    private fun parseSources(parameters: Map<String, String>): List<URI>? {
        val single = parameters[PARAM_URL].orEmpty().takeIf(String::isNotBlank)
        val multiple = parameters[PARAM_URLS].orEmpty().takeIf(String::isNotBlank)
        if (single != null && multiple != null) return null
        val values = when {
            single != null -> listOf(single)
            multiple != null -> multiple.lineSequence().map(String::trim).toList()
            else -> null
        } ?: return null
        if (values.size !in 1..MAXIMUM_SOURCES || values.any(String::isBlank)) return null
        val sources = values.map { value -> parseHttpsUri(value) ?: return null }.distinct()
        return sources.takeIf(List<URI>::isNotEmpty)
    }

    private fun resolveDestination(
        roots: List<NativeCapabilityDestinationRoot>,
        containerPath: String,
    ): File? {
        if (!containerPath.startsWith('/') || containerPath.endsWith('/')) return null
        return roots.firstNotNullOfOrNull { root ->
            val prefix = root.containerPath.trimEnd('/')
            val relative = when {
                containerPath == prefix -> return@firstNotNullOfOrNull null
                containerPath.startsWith("$prefix/") -> containerPath.removePrefix("$prefix/")
                else -> return@firstNotNullOfOrNull null
            }
            val canonicalRoot = runCatching { root.directory.canonicalFile }.getOrNull()
                ?: return@firstNotNullOfOrNull null
            val candidate = runCatching { File(canonicalRoot, relative).canonicalFile }.getOrNull()
                ?: return@firstNotNullOfOrNull null
            candidate.takeIf { file ->
                file != canonicalRoot && file.toPath().startsWith(canonicalRoot.toPath())
            }
        }
    }

    private fun Map<String, String>.intValue(
        key: String,
        defaultValue: Int,
        range: IntRange,
    ): Int? = get(key)
        ?.takeIf(String::isNotBlank)
        ?.toIntOrNull()
        ?.takeIf(range::contains)
        ?: if (get(key).isNullOrBlank()) defaultValue else null

    private fun unsupported(reason: String) = RuntimeProviderDecision.Unsupported(kind, reason)
    private fun blocked(reason: String) = RuntimeProviderDecision.Blocked(kind, reason)

    const val CAPABILITY_ID = "network.download_sha256"
    const val PARAM_URL = "url"
    const val PARAM_URLS = "urls"
    const val PARAM_DESTINATION = "destination"
    const val PARAM_EXPECTED_SHA256 = "expectedSha256"
    const val PARAM_MAX_BYTES = "maxBytes"
    const val PARAM_CONNECT_TIMEOUT_MS = "connectTimeoutMs"
    const val PARAM_READ_TIMEOUT_MS = "readTimeoutMs"
    const val PARAM_MAX_ATTEMPTS = "maxAttempts"
    const val PARAM_RETRY_DELAY_MS = "retryDelayMs"
    const val PARAM_REPLACE_EXISTING = "replaceExisting"

    private val PARAMETERS = setOf(
        PARAM_URL,
        PARAM_URLS,
        PARAM_DESTINATION,
        PARAM_EXPECTED_SHA256,
        PARAM_MAX_BYTES,
        PARAM_CONNECT_TIMEOUT_MS,
        PARAM_READ_TIMEOUT_MS,
        PARAM_MAX_ATTEMPTS,
        PARAM_RETRY_DELAY_MS,
        PARAM_REPLACE_EXISTING,
    )
    private val SHA256 = Regex("[a-f0-9]{64}")
    private const val DEFAULT_CONNECT_TIMEOUT_MS = 30_000
    private const val DEFAULT_READ_TIMEOUT_MS = 60_000
    private const val DEFAULT_MAXIMUM_ATTEMPTS = 1
    private const val DEFAULT_RETRY_DELAY_MS = 1_000L
    private const val MAXIMUM_BYTES = 8L * 1024L * 1024L * 1024L
    private const val MAXIMUM_SOURCES = 8
}

internal fun interface NativeDownloadCancellation {
    fun isCancelled(): Boolean

    /** 生产实现用它关闭正在阻塞读取的连接；缺省实现仍可用于只轮询的轻量调用方。 */
    fun invokeOnCancellation(action: () -> Unit): Closeable = Closeable { }

    companion object {
        val NONE = NativeDownloadCancellation { false }
    }
}

internal class NativeDownloadCancellationSignal : NativeDownloadCancellation {
    private val cancelled = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override fun isCancelled(): Boolean = cancelled.get()

    override fun invokeOnCancellation(action: () -> Unit): Closeable {
        if (cancelled.get()) {
            action()
            return Closeable { }
        }
        listeners += action
        if (cancelled.get() && listeners.remove(action)) action()
        return Closeable { listeners.remove(action) }
    }

    fun cancel(): Boolean {
        if (!cancelled.compareAndSet(false, true)) return false
        listeners.toList().forEach { listener -> runCatching(listener) }
        listeners.clear()
        return true
    }
}

internal fun interface NativeDownloadProgressListener {
    fun onProgress(bytesWritten: Long, contentLength: Long?)

    companion object {
        val NONE = NativeDownloadProgressListener { _, _ -> }
    }
}

internal sealed interface NativeDownloadExecutionResult {
    val attempts: Int

    data class Success(
        val source: URI,
        val destination: File,
        val bytesWritten: Long,
        val actualSha256: String,
        val atomicMove: Boolean,
        override val attempts: Int,
    ) : NativeDownloadExecutionResult

    data class Failure(
        val reason: String,
        override val attempts: Int,
    ) : NativeDownloadExecutionResult

    data class Cancelled(
        val bytesWritten: Long,
        override val attempts: Int,
    ) : NativeDownloadExecutionResult
}

internal interface NativeDownloadConnection : Closeable {
    val responseCode: Int
    val contentLength: Long
    fun header(name: String): String?
    fun inputStream(): InputStream
}

internal fun interface NativeDownloadConnectionFactory {
    @Throws(IOException::class)
    fun open(url: URL, connectTimeoutMs: Int, readTimeoutMs: Int): NativeDownloadConnection
}

internal object HttpUrlNativeDownloadConnectionFactory : NativeDownloadConnectionFactory {
    override fun open(
        url: URL,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): NativeDownloadConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.useCaches = false
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("User-Agent", "Kite-Android-Native-Download/1")
        return object : NativeDownloadConnection {
            override val responseCode: Int get() = connection.responseCode
            override val contentLength: Long get() = connection.contentLengthLong
            override fun header(name: String): String? = connection.getHeaderField(name)
            override fun inputStream(): InputStream = connection.inputStream
            override fun close() = connection.disconnect()
        }
    }
}

/** 执行已验证计划；每次重试都从空临时文件开始，第一版明确不做 Range 续传。 */
internal class AndroidNativeDownloadExecutor(
    private val connectionFactory: NativeDownloadConnectionFactory =
        HttpUrlNativeDownloadConnectionFactory,
) {
    fun execute(
        plan: AndroidNativeDownloadPlan,
        cancellation: NativeDownloadCancellation = NativeDownloadCancellation.NONE,
        progress: NativeDownloadProgressListener = NativeDownloadProgressListener.NONE,
    ): NativeDownloadExecutionResult {
        if (plan.destination.exists() && !plan.replaceExisting) {
            return NativeDownloadExecutionResult.Failure("native_download_destination_exists", 0)
        }
        checkNotNull(plan.destination.parentFile).mkdirs()
        var attempt = 0
        var lastReason = "native_download_failed"
        val sources = listOf(plan.source) + plan.fallbackSources
        val retiredSources = mutableSetOf<URI>()
        for (round in 0 until plan.maximumAttempts) {
            sources.filterNot(retiredSources::contains).forEach { source ->
                attempt += 1
                if (!deleteTemporary(plan.temporaryFile)) {
                    return NativeDownloadExecutionResult.Failure("native_download_temp_cleanup_failed", attempt)
                }
                if (cancelled(cancellation)) {
                    return NativeDownloadExecutionResult.Cancelled(0L, attempt)
                }
                try {
                    val opened = openFollowingRedirects(plan, source)
                    opened.connection.use { connection ->
                        cancellation.invokeOnCancellation(connection::close).use {
                            val contentLength = connection.contentLength.takeIf { it >= 0L }
                            if (contentLength != null && contentLength > plan.maximumBytes) {
                                throw DownloadFailure("native_download_size_limit")
                            }
                            val usableSpace = plan.destination.parentFile?.usableSpace ?: 0L
                            if (contentLength != null && usableSpace > 0L && contentLength > usableSpace) {
                                throw DownloadFailure("native_download_insufficient_space")
                            }
                            val digest = MessageDigest.getInstance("SHA-256")
                            var written = 0L
                            FileOutputStream(plan.temporaryFile, false).use { output ->
                                connection.inputStream().use { input ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    while (true) {
                                        if (cancelled(cancellation)) throw DownloadCancelled(written)
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        if (count == 0) continue
                                        written += count
                                        if (written > plan.maximumBytes) {
                                            throw DownloadFailure("native_download_size_limit")
                                        }
                                        output.write(buffer, 0, count)
                                        digest.update(buffer, 0, count)
                                        progress.onProgress(written, contentLength)
                                    }
                                }
                                output.fd.sync()
                            }
                            val actualSha256 = digest.digest().toHex()
                            if (plan.expectedSha256 != null && actualSha256 != plan.expectedSha256) {
                                throw DownloadFailure("native_download_sha256_mismatch")
                            }
                            val atomicMove = publish(plan)
                            return NativeDownloadExecutionResult.Success(
                                source = source,
                                destination = plan.destination,
                                bytesWritten = written,
                                actualSha256 = actualSha256,
                                atomicMove = atomicMove,
                                attempts = attempt,
                            )
                        }
                    }
                } catch (cancelled: DownloadCancelled) {
                    return if (deleteTemporary(plan.temporaryFile)) {
                        NativeDownloadExecutionResult.Cancelled(cancelled.bytesWritten, attempt)
                    } else {
                        NativeDownloadExecutionResult.Failure("native_download_temp_cleanup_failed", attempt)
                    }
                } catch (failure: DownloadFailure) {
                    if (!deleteTemporary(plan.temporaryFile)) {
                        return NativeDownloadExecutionResult.Failure("native_download_temp_cleanup_failed", attempt)
                    }
                    lastReason = failure.reason
                    if (!failure.allowSourceFallback) {
                        return NativeDownloadExecutionResult.Failure(lastReason, attempt)
                    }
                    if (!failure.retryable) retiredSources += source
                } catch (error: IOException) {
                    if (!deleteTemporary(plan.temporaryFile)) {
                        return NativeDownloadExecutionResult.Failure("native_download_temp_cleanup_failed", attempt)
                    }
                    if (cancelled(cancellation)) {
                        return NativeDownloadExecutionResult.Cancelled(0L, attempt)
                    }
                    lastReason = when {
                        isNoSpace(error) -> "native_download_insufficient_space"
                        isTlsFailure(error) -> "native_download_tls_failure"
                        else -> "native_download_io_failure"
                    }
                    if (lastReason != "native_download_io_failure") {
                        return NativeDownloadExecutionResult.Failure(lastReason, attempt)
                    }
                }
            }
            if (retiredSources.size == sources.size) break
            if (round + 1 < plan.maximumAttempts && !waitForRetry(plan.retryDelayMs, cancellation)) {
                return if (deleteTemporary(plan.temporaryFile)) {
                    NativeDownloadExecutionResult.Cancelled(0L, attempt)
                } else {
                    NativeDownloadExecutionResult.Failure("native_download_temp_cleanup_failed", attempt)
                }
            }
        }
        return if (deleteTemporary(plan.temporaryFile)) {
            NativeDownloadExecutionResult.Failure(lastReason, attempt)
        } else {
            NativeDownloadExecutionResult.Failure("native_download_temp_cleanup_failed", attempt)
        }
    }

    private fun openFollowingRedirects(plan: AndroidNativeDownloadPlan, source: URI): OpenedConnection {
        var current = source
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = connectionFactory.open(
                current.toURL(),
                plan.connectTimeoutMs,
                plan.readTimeoutMs,
            )
            val responseCode = runCatching { connection.responseCode }.getOrElse { error ->
                connection.close()
                throw error
            }
            if (responseCode in REDIRECT_CODES) {
                val location = connection.header("Location")
                connection.close()
                if (redirectCount >= MAX_REDIRECTS) {
                    throw DownloadFailure("native_download_redirect_limit", retryable = false)
                }
                current = runCatching { current.resolve(checkNotNull(location)) }.getOrNull()
                    ?.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
                    ?: throw DownloadFailure("native_download_redirect_invalid", retryable = false)
                return@repeat
            }
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                connection.close()
                throw DownloadFailure("native_download_unexpected_partial", retryable = false)
            }
            if (responseCode !in 200..299) {
                connection.close()
                throw DownloadFailure(
                    reason = "native_download_http_$responseCode",
                    retryable = responseCode == 408 || responseCode == 429 || responseCode >= 500,
                    allowSourceFallback = true,
                )
            }
            return OpenedConnection(connection)
        }
        error("native_download_redirect_loop_unreachable")
    }

    private fun publish(plan: AndroidNativeDownloadPlan): Boolean {
        if (plan.destination.exists() && !plan.replaceExisting) {
            throw DownloadFailure("native_download_destination_exists", retryable = false)
        }
        val replacements = if (plan.replaceExisting) {
            arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            arrayOf(StandardCopyOption.ATOMIC_MOVE)
        }
        return try {
            Files.move(plan.temporaryFile.toPath(), plan.destination.toPath(), *replacements)
            true
        } catch (_: AtomicMoveNotSupportedException) {
            val fallback = if (plan.replaceExisting) {
                arrayOf(StandardCopyOption.REPLACE_EXISTING)
            } else {
                emptyArray()
            }
            Files.move(plan.temporaryFile.toPath(), plan.destination.toPath(), *fallback)
            false
        }
    }

    private fun waitForRetry(delayMs: Long, cancellation: NativeDownloadCancellation): Boolean {
        if (delayMs <= 0L) return !cancelled(cancellation)
        val deadline = System.nanoTime() + delayMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (cancelled(cancellation)) return false
            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1L)
            try {
                Thread.sleep(remainingMs.coerceAtMost(50L))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return !cancelled(cancellation)
    }

    private fun cancelled(cancellation: NativeDownloadCancellation): Boolean =
        Thread.currentThread().isInterrupted || cancellation.isCancelled()

    private fun deleteTemporary(file: File): Boolean = !file.exists() || file.delete() || !file.exists()

    private fun isNoSpace(error: IOException): Boolean =
        generateSequence<Throwable>(error) { it.cause }
            .mapNotNull(Throwable::message)
            .any { message ->
                message.contains("ENOSPC", ignoreCase = true) ||
                    message.contains("No space left", ignoreCase = true)
            }

    private fun isTlsFailure(error: IOException): Boolean =
        generateSequence<Throwable>(error) { it.cause }.any { it is SSLException }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private data class OpenedConnection(
        val connection: NativeDownloadConnection,
    )

    private class DownloadFailure(
        val reason: String,
        val retryable: Boolean = false,
        val allowSourceFallback: Boolean = false,
    ) : IOException(reason)

    private class DownloadCancelled(val bytesWritten: Long) : IOException("native_download_cancelled")

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
