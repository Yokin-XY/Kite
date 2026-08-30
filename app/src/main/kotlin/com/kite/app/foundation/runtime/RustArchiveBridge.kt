package com.kite.app.foundation.runtime

import java.io.File
import org.json.JSONObject

internal data class RustTarArchiveRequest(
    val source: File,
    val destination: File,
    val stagingDirectory: File,
    val maximumArchiveBytes: Long,
    val maximumEntries: Int,
    val maximumTotalBytes: Long,
    val maximumFileBytes: Long,
    val maximumDepth: Int,
    val maximumExpansionRatio: Int,
    val expectedArchiveBytes: Long? = null,
    val expectedSha256: String? = null,
    val compression: RustTarCompression,
    val specialEntryPolicy: RustTarSpecialEntryPolicy,
    val reuseKey: String? = null,
)

internal enum class RustTarCompression(val wireValue: String) {
    NONE("none"),
    GZIP("gzip"),
    XZ("xz"),
}

internal enum class RustTarSpecialEntryPolicy(val wireValue: String) {
    REJECT("reject"),
    SKIP("skip"),
    MATERIALIZE_EMPTY_FILE("materialize_empty_file"),
}

/**
 * 生产 Rust 归档桥。JNI 只持有单次解包事务；进度、取消和业务状态仍由 Kotlin owner 管理。
 */
internal object RustArchiveBridge {
    val isAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("kite_archive_rs")
            true
        }.getOrDefault(false)
    }

    fun execute(
        plan: AndroidNativeArchivePlan,
        cancellation: NativeFileCancellation = NativeFileCancellation.NONE,
        progress: NativeArchiveProgressListener = NativeArchiveProgressListener.NONE,
    ): NativeArchiveExecutionResult {
        if (!isAvailable) return NativeArchiveExecutionResult.Failure("rust_archive_library_unavailable")
        if (cancellation.isCancelled()) return NativeArchiveExecutionResult.Cancelled(0, 0L)
        validateSpace(plan.destination, plan.maximumTotalBytes)?.let { return it }
        if (plan.format != AndroidNativeArchiveFormat.ZIP) {
            val compression = when (plan.format) {
                AndroidNativeArchiveFormat.TAR -> RustTarCompression.NONE
                AndroidNativeArchiveFormat.TAR_GZIP -> RustTarCompression.GZIP
                AndroidNativeArchiveFormat.TAR_XZ -> RustTarCompression.XZ
                AndroidNativeArchiveFormat.ZIP -> error("unreachable")
            }
            return executeTar(
                request = RustTarArchiveRequest(
                    source = plan.source,
                    destination = plan.destination,
                    stagingDirectory = plan.stagingDirectory,
                    maximumArchiveBytes = plan.maximumArchiveBytes,
                    maximumEntries = plan.maximumEntries,
                    maximumTotalBytes = plan.maximumTotalBytes,
                    maximumFileBytes = plan.maximumFileBytes,
                    maximumDepth = plan.maximumDepth,
                    maximumExpansionRatio = plan.maximumExpansionRatio,
                    expectedArchiveBytes = plan.source.length(),
                    expectedSha256 = plan.expectedSha256,
                    compression = compression,
                    specialEntryPolicy = plan.specialEntryPolicy,
                    reuseKey = plan.reuseKey,
                ),
                cancellation = cancellation,
                progress = progress,
            )
        }
        val request = JSONObject()
            .put("source", plan.source.absolutePath)
            .put("destination", plan.destination.absolutePath)
            .put("stagingDirectory", plan.stagingDirectory.absolutePath)
            .put("maximumArchiveBytes", plan.maximumArchiveBytes)
            .put("maximumEntries", plan.maximumEntries)
            .put("maximumTotalBytes", plan.maximumTotalBytes)
            .put("maximumFileBytes", plan.maximumFileBytes)
            .put("maximumDepth", plan.maximumDepth)
            .put("maximumExpansionRatio", plan.maximumExpansionRatio)
            .apply { plan.reuseKey?.let { put("reuseKey", it) } }
        return parseResponse(
            runCatching {
                extractZipNative(request.toString(), observer(cancellation, progress))
            }.getOrNull()
        )
    }

    fun executeTar(
        request: RustTarArchiveRequest,
        cancellation: NativeFileCancellation = NativeFileCancellation.NONE,
        progress: NativeArchiveProgressListener = NativeArchiveProgressListener.NONE,
    ): NativeArchiveExecutionResult {
        if (!isAvailable) return NativeArchiveExecutionResult.Failure("rust_archive_library_unavailable")
        if (cancellation.isCancelled()) return NativeArchiveExecutionResult.Cancelled(0, 0L)
        validateSpace(request.destination, request.maximumTotalBytes)?.let { return it }
        val payload = JSONObject()
            .put("source", request.source.absolutePath)
            .put("destination", request.destination.absolutePath)
            .put("stagingDirectory", request.stagingDirectory.absolutePath)
            .put("maximumArchiveBytes", request.maximumArchiveBytes)
            .put("maximumEntries", request.maximumEntries)
            .put("maximumTotalBytes", request.maximumTotalBytes)
            .put("maximumFileBytes", request.maximumFileBytes)
            .put("maximumDepth", request.maximumDepth)
            .put("maximumExpansionRatio", request.maximumExpansionRatio)
            .put("compression", request.compression.wireValue)
            .put("specialEntryPolicy", request.specialEntryPolicy.wireValue)
            .apply { request.reuseKey?.let { put("reuseKey", it) } }
        request.expectedArchiveBytes?.let { payload.put("expectedArchiveBytes", it) }
        request.expectedSha256?.takeIf(String::isNotBlank)?.let { payload.put("expectedSha256", it) }
        return parseResponse(
            runCatching {
                extractTarNative(payload.toString(), observer(cancellation, progress))
            }.getOrNull()
        )
    }

    private fun observer(
        cancellation: NativeFileCancellation,
        progress: NativeArchiveProgressListener,
    ) = RustArchiveProgressObserver { entries, bytes ->
        progress.onProgress(entries, bytes)
        !cancellation.isCancelled()
    }

    private fun validateSpace(destination: File, maximumTotalBytes: Long): NativeArchiveExecutionResult.Failure? {
        val destinationParent = destination.parentFile
            ?: return NativeArchiveExecutionResult.Failure("native_archive_destination_invalid")
        if (!destinationParent.mkdirs() && !destinationParent.isDirectory) {
            return NativeArchiveExecutionResult.Failure("native_archive_destination_parent_failed")
        }
        val usableSpace = destinationParent.usableSpace
        return if (usableSpace > 0L && maximumTotalBytes > usableSpace) {
            NativeArchiveExecutionResult.Failure("native_archive_insufficient_space")
        } else {
            null
        }
    }

    private fun parseResponse(raw: String?): NativeArchiveExecutionResult {
        val response = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return NativeArchiveExecutionResult.Failure("rust_archive_bridge_failed")
        val entries = response.optInt("entriesExtracted", 0)
        val bytes = response.optLong("bytesExtracted", 0L)
        return when (response.optString("status")) {
            "success" -> NativeArchiveExecutionResult.Success(entries, bytes)
            "cancelled" -> NativeArchiveExecutionResult.Cancelled(entries, bytes)
            else -> NativeArchiveExecutionResult.Failure(
                response.optString("reason").takeIf(String::isNotBlank) ?: "rust_archive_failed"
            )
        }
    }

    private external fun extractZipNative(requestJson: String, observer: RustArchiveProgressObserver): String
    private external fun extractTarNative(requestJson: String, observer: RustArchiveProgressObserver): String
}

internal fun interface RustArchiveProgressObserver {
    /** 返回 false 表示调用方已经取消本次事务。 */
    fun onArchiveProgress(entriesExtracted: Int, bytesExtracted: Long): Boolean
}
