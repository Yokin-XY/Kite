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
    val expectedArchiveBytes: Long,
    val expectedSha256: String,
    val compression: RustTarCompression,
    val specialEntryPolicy: RustTarSpecialEntryPolicy,
)

internal enum class RustTarCompression(val wireValue: String) {
    GZIP("gzip"),
    XZ("xz"),
}

internal enum class RustTarSpecialEntryPolicy(val wireValue: String) {
    REJECT("reject"),
    SKIP("skip"),
    MATERIALIZE_EMPTY_FILE("materialize_empty_file"),
}

/** Debug-only Rust archive engine. Production routing continues to use the existing providers. */
internal object RustArchiveBridge {
    val isAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("kite_archive_rs")
            true
        }.getOrDefault(false)
    }

    fun execute(
        plan: AndroidNativeArchivePlan,
        cancelAfterBytes: Long? = null,
    ): NativeArchiveExecutionResult {
        if (!isAvailable) return NativeArchiveExecutionResult.Failure("rust_archive_library_unavailable")
        val destinationParent = plan.destination.parentFile
            ?: return NativeArchiveExecutionResult.Failure("native_archive_destination_invalid")
        val usableSpace = destinationParent.usableSpace
        if (usableSpace > 0L && plan.maximumTotalBytes > usableSpace) {
            return NativeArchiveExecutionResult.Failure("native_archive_insufficient_space")
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
            .apply { cancelAfterBytes?.let { put("cancelAfterBytes", it) } }
        return parseResponse(runCatching { extractZipNative(request.toString()) }.getOrNull())
    }

    fun executeTar(
        request: RustTarArchiveRequest,
        cancelAfterBytes: Long? = null,
    ): NativeArchiveExecutionResult {
        if (!isAvailable) return NativeArchiveExecutionResult.Failure("rust_archive_library_unavailable")
        val destinationParent = request.destination.parentFile
            ?: return NativeArchiveExecutionResult.Failure("native_archive_destination_invalid")
        val usableSpace = destinationParent.usableSpace
        if (usableSpace > 0L && request.maximumTotalBytes > usableSpace) {
            return NativeArchiveExecutionResult.Failure("native_archive_insufficient_space")
        }
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
            .put("expectedArchiveBytes", request.expectedArchiveBytes)
            .put("expectedSha256", request.expectedSha256)
            .put("compression", request.compression.wireValue)
            .put("specialEntryPolicy", request.specialEntryPolicy.wireValue)
            .apply { cancelAfterBytes?.let { put("cancelAfterBytes", it) } }
        return parseResponse(runCatching { extractTarNative(payload.toString()) }.getOrNull())
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

    private external fun extractZipNative(requestJson: String): String
    private external fun extractTarNative(requestJson: String): String
}
