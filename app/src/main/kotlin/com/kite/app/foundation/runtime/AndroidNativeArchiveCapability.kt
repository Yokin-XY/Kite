package com.kite.app.foundation.runtime

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile

internal data class AndroidNativeArchivePlan(
    val source: File,
    val destination: File,
    val stagingDirectory: File,
    val maximumArchiveBytes: Long,
    val maximumEntries: Int,
    val maximumTotalBytes: Long,
    val maximumFileBytes: Long,
    val maximumDepth: Int,
    val maximumExpansionRatio: Int,
    val format: AndroidNativeArchiveFormat = AndroidNativeArchiveFormat.ZIP,
    val expectedSha256: String? = null,
    val specialEntryPolicy: RustTarSpecialEntryPolicy = RustTarSpecialEntryPolicy.REJECT,
    val reuseKey: String? = null,
)

internal enum class AndroidNativeArchiveFormat(val wireValue: String) {
    ZIP("zip"),
    TAR("tar"),
    TAR_GZIP("tar.gz"),
    TAR_XZ("tar.xz"),
}

/** 只接受显式格式和资源上限；业务事务、路径授权与取消仍由 Kotlin owner 持有。 */
internal object AndroidNativeArchiveCapabilityProvider :
    RuntimeExecutionProvider<AndroidNativeFileCapabilityContext, AndroidNativeArchivePlan> {
    override val kind: RuntimeProviderKind = RuntimeProviderKind.ANDROID_NATIVE

    override fun prepare(
        context: AndroidNativeFileCapabilityContext,
        request: RuntimeExecutionRequest,
    ): RuntimeProviderDecision<AndroidNativeArchivePlan> {
        val payload = request.payload as? RuntimeExecutionPayload.NativeCapability
            ?: return unsupported("native_capability_payload_required")
        if (payload.capabilityId != CAPABILITY_ID) return unsupported("native_archive_capability_not_supported")
        if (request.workingDirectory != null || request.environment.isNotEmpty() ||
            request.guarantees.isNotEmpty() || request.guaranteeEvidence.isNotEmpty()
        ) {
            return blocked("native_archive_ignored_request_fields")
        }
        if (request.requirements.any { it != RuntimeExecutionRequirement.ANDROID_NATIVE }) {
            return blocked("native_archive_requirement_conflict")
        }
        val parameters = payload.parameters.mapValues { (_, value) -> value.trim() }
        if (parameters.keys.any { it !in PARAMETERS }) return blocked("native_archive_parameter_unknown")
        val format = AndroidNativeArchiveFormat.entries.firstOrNull {
            it.wireValue == parameters[PARAM_FORMAT]?.lowercase()
        } ?: return blocked("native_archive_format_unsupported")
        val source = NativeFilePathResolver.resolve(context, parameters[PARAM_SOURCE], NativeFilePermission.READ)
            ?: return blocked("native_archive_source_invalid")
        val destination = NativeFilePathResolver.resolve(
            context,
            parameters[PARAM_DESTINATION],
            NativeFilePermission.CREATE,
        ) ?: return blocked("native_archive_destination_invalid")
        if (source.file == destination.file) return blocked("native_archive_same_path")
        val maximumArchiveBytes = parameters.longValue(PARAM_MAX_ARCHIVE_BYTES, 1L..MAXIMUM_BYTES)
            ?: return blocked("native_archive_max_archive_bytes_invalid")
        val maximumEntries = parameters.intValue(PARAM_MAX_ENTRIES, 1..MAXIMUM_ENTRIES)
            ?: return blocked("native_archive_max_entries_invalid")
        val maximumTotalBytes = parameters.longValue(PARAM_MAX_TOTAL_BYTES, 1L..MAXIMUM_BYTES)
            ?: return blocked("native_archive_max_total_bytes_invalid")
        val maximumFileBytes = parameters.longValue(PARAM_MAX_FILE_BYTES, 1L..maximumTotalBytes)
            ?: return blocked("native_archive_max_file_bytes_invalid")
        val maximumDepth = parameters.intValue(PARAM_MAX_DEPTH, 1..MAXIMUM_DEPTH)
            ?: return blocked("native_archive_max_depth_invalid")
        val maximumExpansionRatio = parameters.intValue(PARAM_MAX_EXPANSION_RATIO, 1..MAXIMUM_EXPANSION_RATIO)
            ?: return blocked("native_archive_max_expansion_ratio_invalid")
        val expectedSha256 = parameters[PARAM_EXPECTED_SHA256]
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
        if (expectedSha256 != null && !SHA256.matches(expectedSha256)) {
            return blocked("native_archive_digest_invalid")
        }
        val specialEntryPolicyValue = parameters[PARAM_SPECIAL_ENTRY_POLICY]?.takeIf(String::isNotBlank)
        val specialEntryPolicy = specialEntryPolicyValue?.let { value ->
            RustTarSpecialEntryPolicy.entries.firstOrNull { it.wireValue == value.lowercase() }
                ?: return blocked("native_archive_special_entry_policy_invalid")
        } ?: RustTarSpecialEntryPolicy.REJECT
        if (format == AndroidNativeArchiveFormat.ZIP && specialEntryPolicy != RustTarSpecialEntryPolicy.REJECT) {
            return blocked("native_archive_special_entry_policy_invalid")
        }
        val reuseKey = parameters[PARAM_REUSE_KEY]?.takeIf(String::isNotBlank)
        if (reuseKey != null && !REUSE_KEY.matches(reuseKey)) {
            return blocked("native_archive_reuse_key_invalid")
        }
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = AndroidNativeArchivePlan(
                source = source.file,
                destination = destination.file,
                stagingDirectory = File(
                    checkNotNull(destination.file.parentFile),
                    ".${destination.file.name}.kite-extract-${UUID.randomUUID().toString().replace("-", "")}.part",
                ),
                maximumArchiveBytes = maximumArchiveBytes,
                maximumEntries = maximumEntries,
                maximumTotalBytes = maximumTotalBytes,
                maximumFileBytes = maximumFileBytes,
                maximumDepth = maximumDepth,
                maximumExpansionRatio = maximumExpansionRatio,
                format = format,
                expectedSha256 = expectedSha256,
                specialEntryPolicy = specialEntryPolicy,
                reuseKey = reuseKey,
            ),
            reason = "native_archive_${format.wireValue.replace('.', '_')}_ready",
        )
    }

    private fun Map<String, String>.longValue(key: String, range: LongRange): Long? =
        get(key)?.toLongOrNull()?.takeIf(range::contains)

    private fun Map<String, String>.intValue(key: String, range: IntRange): Int? =
        get(key)?.toIntOrNull()?.takeIf(range::contains)

    private fun unsupported(reason: String) = RuntimeProviderDecision.Unsupported(kind, reason)
    private fun blocked(reason: String) = RuntimeProviderDecision.Blocked(kind, reason)

    const val CAPABILITY_ID = "archive.extract_safe"
    const val PARAM_SOURCE = "source"
    const val PARAM_DESTINATION = "destination"
    const val PARAM_FORMAT = "format"
    const val PARAM_MAX_ARCHIVE_BYTES = "maxArchiveBytes"
    const val PARAM_MAX_ENTRIES = "maxEntries"
    const val PARAM_MAX_TOTAL_BYTES = "maxTotalBytes"
    const val PARAM_MAX_FILE_BYTES = "maxFileBytes"
    const val PARAM_MAX_DEPTH = "maxDepth"
    const val PARAM_MAX_EXPANSION_RATIO = "maxExpansionRatio"
    const val PARAM_EXPECTED_SHA256 = "expectedSha256"
    const val PARAM_SPECIAL_ENTRY_POLICY = "specialEntryPolicy"
    const val PARAM_REUSE_KEY = "reuseKey"
    const val FORMAT_ZIP = "zip"

    private val PARAMETERS = setOf(
        PARAM_SOURCE,
        PARAM_DESTINATION,
        PARAM_FORMAT,
        PARAM_MAX_ARCHIVE_BYTES,
        PARAM_MAX_ENTRIES,
        PARAM_MAX_TOTAL_BYTES,
        PARAM_MAX_FILE_BYTES,
        PARAM_MAX_DEPTH,
        PARAM_MAX_EXPANSION_RATIO,
        PARAM_EXPECTED_SHA256,
        PARAM_SPECIAL_ENTRY_POLICY,
        PARAM_REUSE_KEY,
    )
    private val SHA256 = Regex("[a-f0-9]{64}")
    private val REUSE_KEY = Regex("[A-Za-z0-9._:+-]{1,160}")
    private const val MAXIMUM_ENTRIES = 100_000
    private const val MAXIMUM_DEPTH = 64
    private const val MAXIMUM_EXPANSION_RATIO = 1_000
    private const val MAXIMUM_BYTES = 8L * 1024L * 1024L * 1024L
}

internal fun interface NativeArchiveProgressListener {
    fun onProgress(entriesExtracted: Int, bytesExtracted: Long)

    companion object {
        val NONE = NativeArchiveProgressListener { _, _ -> }
    }
}

internal sealed interface NativeArchiveExecutionResult {
    data class Success(val entriesExtracted: Int, val bytesExtracted: Long) : NativeArchiveExecutionResult
    data class Failure(val reason: String) : NativeArchiveExecutionResult
    data class Cancelled(val entriesExtracted: Int, val bytesExtracted: Long) : NativeArchiveExecutionResult
}

internal class AndroidNativeArchiveExecutor(
    private val platform: NativeFilePlatform = JavaNativeFilePlatform,
    private val availableBytes: (File) -> Long = File::getUsableSpace,
) {
    fun execute(
        plan: AndroidNativeArchivePlan,
        cancellation: NativeFileCancellation = NativeFileCancellation.NONE,
        progress: NativeArchiveProgressListener = NativeArchiveProgressListener.NONE,
    ): NativeArchiveExecutionResult {
        if (plan.format != AndroidNativeArchiveFormat.ZIP) {
            return NativeArchiveExecutionResult.Failure("native_archive_format_requires_rust")
        }
        validateSource(plan)?.let { return it }
        if (plan.destination.exists()) return NativeArchiveExecutionResult.Failure("native_archive_destination_exists")
        val destinationParent = plan.destination.parentFile
            ?: return NativeArchiveExecutionResult.Failure("native_archive_destination_invalid")
        if (!destinationParent.mkdirs() && !destinationParent.isDirectory) {
            return NativeArchiveExecutionResult.Failure("native_archive_destination_parent_failed")
        }
        val usableSpace = availableBytes(destinationParent)
        if (usableSpace > 0L && plan.maximumTotalBytes > usableSpace) {
            return NativeArchiveExecutionResult.Failure("native_archive_insufficient_space")
        }
        if (!deleteTree(plan.stagingDirectory.toPath())) {
            return NativeArchiveExecutionResult.Failure("native_archive_staging_cleanup_failed")
        }
        if (!plan.stagingDirectory.mkdirs() && !plan.stagingDirectory.isDirectory) {
            return NativeArchiveExecutionResult.Failure("native_archive_staging_create_failed")
        }
        return try {
            val extracted = extractZip(plan, cancellation, progress)
            if (cancellation.isCancelled()) throw ArchiveCancelled(extracted.entries, extracted.bytes)
            platform.atomicMove(plan.stagingDirectory.toPath(), plan.destination.toPath(), false)
            NativeArchiveExecutionResult.Success(extracted.entries, extracted.bytes)
        } catch (cancelled: ArchiveCancelled) {
            if (deleteTree(plan.stagingDirectory.toPath())) {
                NativeArchiveExecutionResult.Cancelled(cancelled.entries, cancelled.bytes)
            } else {
                NativeArchiveExecutionResult.Failure("native_archive_staging_cleanup_failed")
            }
        } catch (failure: ArchiveFailure) {
            deleteTree(plan.stagingDirectory.toPath())
            NativeArchiveExecutionResult.Failure(failure.reason)
        } catch (error: Throwable) {
            deleteTree(plan.stagingDirectory.toPath())
            NativeArchiveExecutionResult.Failure(reason(error))
        }
    }

    private data class Extracted(val entries: Int, val bytes: Long)
    private data class SafeZipEntry(
        val name: String,
        val relativePath: String,
        val directory: Boolean,
        val declaredSize: Long,
    )

    private fun extractZip(
        plan: AndroidNativeArchivePlan,
        cancellation: NativeFileCancellation,
        progress: NativeArchiveProgressListener,
    ): Extracted {
        val safeEntries = preflightZip(plan, cancellation)
        var entries = 0
        var totalBytes = 0L
        ZipArchiveInputStream(BufferedInputStream(FileInputStream(plan.source)), "UTF-8", true, true).use { input ->
            while (true) {
                if (cancellation.isCancelled()) throw ArchiveCancelled(entries, totalBytes)
                val entry = input.nextZipEntry ?: break
                val expected = safeEntries.getOrNull(entries)
                    ?: throw ArchiveFailure("native_archive_central_directory_mismatch")
                if (entry.name != expected.name || entry.isDirectory != expected.directory) {
                    throw ArchiveFailure("native_archive_central_directory_mismatch")
                }
                entries += 1
                if (!input.canReadEntryData(entry)) throw ArchiveFailure("native_archive_entry_unreadable")
                val output = resolveOutput(plan.stagingDirectory, expected.relativePath)
                if (expected.directory) {
                    if (!output.mkdirs() && !output.isDirectory) throw IOException("directory_create_failed")
                    progress.onProgress(entries, totalBytes)
                    continue
                }
                val parent = output.parentFile ?: throw ArchiveFailure("native_archive_path_invalid")
                if (!parent.mkdirs() && !parent.isDirectory) throw IOException("parent_create_failed")
                var fileBytes = 0L
                FileOutputStream(output, false).use { stream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancellation.isCancelled()) throw ArchiveCancelled(entries, totalBytes)
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        fileBytes += count
                        totalBytes += count
                        if (fileBytes > plan.maximumFileBytes) throw ArchiveFailure("native_archive_file_size_limit")
                        if (totalBytes > plan.maximumTotalBytes) throw ArchiveFailure("native_archive_total_size_limit")
                        if (totalBytes > plan.source.length() * plan.maximumExpansionRatio.toLong()) {
                            throw ArchiveFailure("native_archive_expansion_ratio_limit")
                        }
                        stream.write(buffer, 0, count)
                        progress.onProgress(entries, totalBytes)
                    }
                }
                if (expected.declaredSize >= 0L && fileBytes != expected.declaredSize) {
                    throw ArchiveFailure("native_archive_central_directory_mismatch")
                }
            }
        }
        if (entries != safeEntries.size) throw ArchiveFailure("native_archive_central_directory_mismatch")
        return Extracted(entries, totalBytes)
    }

    private fun preflightZip(
        plan: AndroidNativeArchivePlan,
        cancellation: NativeFileCancellation,
    ): List<SafeZipEntry> {
        val result = ArrayList<SafeZipEntry>()
        val seen = hashSetOf<String>()
        var declaredTotal = 0L
        ZipFile(plan.source).use { archive ->
            val entries = archive.entries
            while (entries.hasMoreElements()) {
                if (cancellation.isCancelled()) throw ArchiveCancelled(result.size, 0L)
                val entry = entries.nextElement()
                if (result.size >= plan.maximumEntries) throw ArchiveFailure("native_archive_entry_limit")
                if (!archive.canReadEntryData(entry)) throw ArchiveFailure("native_archive_entry_unreadable")
                val relative = safeRelativePath(entry.name, plan.maximumDepth)
                if (!seen.add(relative)) throw ArchiveFailure("native_archive_duplicate_entry")
                if (!entry.isDirectory && !isRegularFile(entry)) {
                    throw ArchiveFailure("native_archive_special_entry")
                }
                val declaredSize = entry.size
                if (declaredSize > plan.maximumFileBytes) throw ArchiveFailure("native_archive_file_size_limit")
                if (declaredSize > 0L) {
                    declaredTotal += declaredSize
                    if (declaredTotal > plan.maximumTotalBytes) {
                        throw ArchiveFailure("native_archive_total_size_limit")
                    }
                }
                result += SafeZipEntry(entry.name, relative, entry.isDirectory, declaredSize)
            }
        }
        return result
    }

    private fun validateSource(plan: AndroidNativeArchivePlan): NativeArchiveExecutionResult.Failure? = when {
        !Files.exists(plan.source.toPath(), LinkOption.NOFOLLOW_LINKS) ->
            NativeArchiveExecutionResult.Failure("native_archive_source_missing")
        Files.isSymbolicLink(plan.source.toPath()) ->
            NativeArchiveExecutionResult.Failure("native_archive_source_symlink")
        !Files.isRegularFile(plan.source.toPath(), LinkOption.NOFOLLOW_LINKS) ->
            NativeArchiveExecutionResult.Failure("native_archive_source_not_regular")
        plan.source.length() > plan.maximumArchiveBytes ->
            NativeArchiveExecutionResult.Failure("native_archive_source_size_limit")
        else -> null
    }

    private fun safeRelativePath(raw: String, maximumDepth: Int): String {
        if (raw.isBlank() || '\u0000' in raw || '\\' in raw || raw.startsWith('/') || DRIVE_PREFIX.containsMatchIn(raw)) {
            throw ArchiveFailure("native_archive_path_invalid")
        }
        val normalized = raw.removeSuffix("/")
        val segments = normalized.split('/')
        if (segments.isEmpty() || segments.size > maximumDepth ||
            segments.any { it.isBlank() || it == "." || it == ".." }
        ) {
            throw ArchiveFailure(if (segments.size > maximumDepth) "native_archive_depth_limit" else "native_archive_path_invalid")
        }
        if (normalized.toByteArray(Charsets.UTF_8).size > MAXIMUM_PATH_BYTES) {
            throw ArchiveFailure("native_archive_path_length_limit")
        }
        return normalized
    }

    private fun resolveOutput(root: File, relative: String): File {
        val rootPath = root.toPath().toAbsolutePath().normalize()
        val output = rootPath.resolve(relative).normalize()
        if (output == rootPath || !output.startsWith(rootPath)) throw ArchiveFailure("native_archive_path_invalid")
        return output.toFile()
    }

    private fun isRegularFile(entry: ZipArchiveEntry): Boolean {
        if (entry.isUnixSymlink) return false
        val unixType = entry.unixMode and UNIX_TYPE_MASK
        return unixType == 0 || unixType == UNIX_REGULAR_FILE
    }

    private fun deleteTree(root: Path): Boolean = runCatching {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return@runCatching
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(path)
                } else {
                    Files.deleteIfExists(path)
                }
            }
        }
    }.isSuccess

    private fun reason(error: Throwable): String = when (error) {
        is AtomicMoveNotSupportedException -> "native_archive_atomic_move_unsupported"
        is AccessDeniedException, is SecurityException -> "native_archive_permission_denied"
        is IOException -> "native_archive_io_failure"
        else -> "native_archive_failed"
    }

    private class ArchiveFailure(val reason: String) : IOException()
    private class ArchiveCancelled(val entries: Int, val bytes: Long) : IOException()

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAXIMUM_PATH_BYTES = 4_096
        const val UNIX_TYPE_MASK = 0xF000
        const val UNIX_REGULAR_FILE = 0x8000
        val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    }
}
