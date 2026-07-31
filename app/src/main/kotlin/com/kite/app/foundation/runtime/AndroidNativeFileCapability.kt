package com.kite.app.foundation.runtime

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal enum class NativeFilePermission {
    READ,
    CREATE,
    REPLACE,
    REMOVE,
}

/**
 * 原生文件能力的显式授权根。更具体的根优先，避免宽根权限覆盖缓存等受控子目录。
 */
internal data class NativeFileCapabilityRoot(
    val containerPath: String,
    val directory: File,
    val permissions: Set<NativeFilePermission>,
) {
    init {
        require(containerPath.startsWith('/')) { "native_file_root_not_absolute" }
        require(containerPath != "/") { "native_file_root_too_broad" }
        require(!containerPath.endsWith('/')) { "native_file_root_trailing_slash" }
        require(permissions.isNotEmpty()) { "native_file_root_permissions_missing" }
    }
}

internal data class AndroidNativeFileCapabilityContext(
    val roots: List<NativeFileCapabilityRoot>,
) {
    init {
        require(roots.isNotEmpty()) { "native_file_roots_missing" }
    }
}

internal sealed interface AndroidNativeFilePlan {
    val capabilityId: String

    data class CopyFile(
        val source: File,
        val destination: File,
        val destinationPermissions: Set<NativeFilePermission>,
        val temporaryFile: File,
        val maximumBytes: Long,
        val replaceExisting: Boolean,
    ) : AndroidNativeFilePlan {
        override val capabilityId: String = AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE
    }

    data class MoveFile(
        val source: File,
        val destination: File,
        val destinationPermissions: Set<NativeFilePermission>,
        val replaceExisting: Boolean,
    ) : AndroidNativeFilePlan {
        override val capabilityId: String = AndroidNativeFileCapabilityProvider.CAPABILITY_MOVE_FILE
    }

    data class DeleteFile(
        val target: File,
    ) : AndroidNativeFilePlan {
        override val capabilityId: String = AndroidNativeFileCapabilityProvider.CAPABILITY_DELETE_FILE
    }
}

/** 只编译受授权根内、无符号链接穿越的单文件操作。 */
internal object AndroidNativeFileCapabilityProvider :
    RuntimeExecutionProvider<AndroidNativeFileCapabilityContext, AndroidNativeFilePlan> {
    override val kind: RuntimeProviderKind = RuntimeProviderKind.ANDROID_NATIVE

    override fun prepare(
        context: AndroidNativeFileCapabilityContext,
        request: RuntimeExecutionRequest,
    ): RuntimeProviderDecision<AndroidNativeFilePlan> {
        val payload = request.payload as? RuntimeExecutionPayload.NativeCapability
            ?: return unsupported("native_capability_payload_required")
        if (payload.capabilityId !in CAPABILITIES) {
            return unsupported("native_file_capability_not_supported")
        }
        if (request.workingDirectory != null || request.environment.isNotEmpty() ||
            request.guarantees.isNotEmpty() || request.guaranteeEvidence.isNotEmpty()
        ) {
            return blocked("native_file_ignored_request_fields")
        }
        if (request.requirements.any { it != RuntimeExecutionRequirement.ANDROID_NATIVE }) {
            return blocked("native_file_requirement_conflict")
        }
        val parameters = payload.parameters.mapValues { (_, value) -> value.trim() }
        return when (payload.capabilityId) {
            CAPABILITY_COPY_FILE -> prepareCopy(context, parameters)
            CAPABILITY_MOVE_FILE -> prepareMove(context, parameters)
            CAPABILITY_DELETE_FILE -> prepareDelete(context, parameters)
            else -> unsupported("native_file_capability_not_supported")
        }
    }

    private fun prepareCopy(
        context: AndroidNativeFileCapabilityContext,
        parameters: Map<String, String>,
    ): RuntimeProviderDecision<AndroidNativeFilePlan> {
        if (parameters.keys.any { it !in COPY_PARAMETERS }) {
            return blocked("native_file_parameter_unknown")
        }
        val source = resolve(context, parameters[PARAM_SOURCE], NativeFilePermission.READ)
            ?: return blocked("native_file_source_invalid")
        val destination = resolve(context, parameters[PARAM_DESTINATION], NativeFilePermission.CREATE)
            ?: return blocked("native_file_destination_invalid")
        if (source.file == destination.file) return blocked("native_file_same_path")
        val maximumBytes = parameters[PARAM_MAX_BYTES]
            ?.toLongOrNull()
            ?.takeIf { it in 1..MAXIMUM_BYTES }
            ?: return blocked("native_file_max_bytes_invalid")
        val replaceExisting = parseBoolean(parameters[PARAM_REPLACE_EXISTING])
            ?: return blocked("native_file_replace_existing_invalid")
        if (replaceExisting && NativeFilePermission.REPLACE !in destination.root.permissions) {
            return blocked("native_file_replace_not_authorized")
        }
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = AndroidNativeFilePlan.CopyFile(
                source = source.file,
                destination = destination.file,
                destinationPermissions = destination.root.permissions,
                temporaryFile = temporaryFile(destination.file, "copy"),
                maximumBytes = maximumBytes,
                replaceExisting = replaceExisting,
            ),
            reason = "native_file_copy_ready",
        )
    }

    private fun prepareMove(
        context: AndroidNativeFileCapabilityContext,
        parameters: Map<String, String>,
    ): RuntimeProviderDecision<AndroidNativeFilePlan> {
        if (parameters.keys.any { it !in MOVE_PARAMETERS }) {
            return blocked("native_file_parameter_unknown")
        }
        val source = resolve(context, parameters[PARAM_SOURCE], NativeFilePermission.REMOVE)
            ?: return blocked("native_file_source_remove_not_authorized")
        val destination = resolve(context, parameters[PARAM_DESTINATION], NativeFilePermission.CREATE)
            ?: return blocked("native_file_destination_invalid")
        if (source.file == destination.file) return blocked("native_file_same_path")
        val replaceExisting = parseBoolean(parameters[PARAM_REPLACE_EXISTING])
            ?: return blocked("native_file_replace_existing_invalid")
        if (replaceExisting && NativeFilePermission.REPLACE !in destination.root.permissions) {
            return blocked("native_file_replace_not_authorized")
        }
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = AndroidNativeFilePlan.MoveFile(
                source = source.file,
                destination = destination.file,
                destinationPermissions = destination.root.permissions,
                replaceExisting = replaceExisting,
            ),
            reason = "native_file_move_ready",
        )
    }

    private fun prepareDelete(
        context: AndroidNativeFileCapabilityContext,
        parameters: Map<String, String>,
    ): RuntimeProviderDecision<AndroidNativeFilePlan> {
        if (parameters.keys.any { it !in DELETE_PARAMETERS }) {
            return blocked("native_file_parameter_unknown")
        }
        val target = resolve(context, parameters[PARAM_TARGET], NativeFilePermission.REMOVE)
            ?: return blocked("native_file_delete_not_authorized")
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = AndroidNativeFilePlan.DeleteFile(target.file),
            reason = "native_file_delete_ready",
        )
    }

    private data class ResolvedPath(
        val root: NativeFileCapabilityRoot,
        val file: File,
    )

    private fun resolve(
        context: AndroidNativeFileCapabilityContext,
        rawPath: String?,
        permission: NativeFilePermission,
    ): ResolvedPath? {
        val containerPath = rawPath?.takeIf { it.startsWith('/') && !it.endsWith('/') } ?: return null
        return context.roots
            .sortedByDescending { it.containerPath.length }
            .firstNotNullOfOrNull { root ->
                if (permission !in root.permissions) return@firstNotNullOfOrNull null
                val prefix = root.containerPath
                if (!containerPath.startsWith("$prefix/")) return@firstNotNullOfOrNull null
                val segments = containerPath.removePrefix("$prefix/").split('/')
                if (segments.isEmpty() || segments.any { it.isBlank() || it == "." || it == ".." }) {
                    return@firstNotNullOfOrNull null
                }
                val rootPath = root.directory.toPath().toAbsolutePath().normalize()
                val candidate = segments.fold(rootPath) { path, segment -> path.resolve(segment) }.normalize()
                if (candidate == rootPath || !candidate.startsWith(rootPath)) return@firstNotNullOfOrNull null
                if (containsExistingSymlink(rootPath, candidate)) return@firstNotNullOfOrNull null
                ResolvedPath(root, candidate.toFile())
            }
    }

    private fun containsExistingSymlink(root: Path, candidate: Path): Boolean {
        var cursor = root
        for (segment in root.relativize(candidate)) {
            cursor = cursor.resolve(segment)
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) return true
        }
        return false
    }

    private fun parseBoolean(value: String?): Boolean? = when (value?.lowercase().orEmpty()) {
        "", "false" -> false
        "true" -> true
        else -> null
    }

    private fun temporaryFile(destination: File, operation: String): File = File(
        checkNotNull(destination.parentFile),
        ".${destination.name}.kite-$operation-${UUID.randomUUID().toString().replace("-", "")}.part",
    )

    private fun unsupported(reason: String) = RuntimeProviderDecision.Unsupported(kind, reason)
    private fun blocked(reason: String) = RuntimeProviderDecision.Blocked(kind, reason)

    const val CAPABILITY_COPY_FILE = "filesystem.copy_file_atomic"
    const val CAPABILITY_MOVE_FILE = "filesystem.move_file_atomic"
    const val CAPABILITY_DELETE_FILE = "filesystem.delete_file"
    const val PARAM_SOURCE = "source"
    const val PARAM_DESTINATION = "destination"
    const val PARAM_TARGET = "target"
    const val PARAM_MAX_BYTES = "maxBytes"
    const val PARAM_REPLACE_EXISTING = "replaceExisting"

    private val CAPABILITIES = setOf(CAPABILITY_COPY_FILE, CAPABILITY_MOVE_FILE, CAPABILITY_DELETE_FILE)
    private val COPY_PARAMETERS = setOf(PARAM_SOURCE, PARAM_DESTINATION, PARAM_MAX_BYTES, PARAM_REPLACE_EXISTING)
    private val MOVE_PARAMETERS = setOf(PARAM_SOURCE, PARAM_DESTINATION, PARAM_REPLACE_EXISTING)
    private val DELETE_PARAMETERS = setOf(PARAM_TARGET)
    private const val MAXIMUM_BYTES = 8L * 1024L * 1024L * 1024L
}

internal fun interface NativeFileCancellation {
    fun isCancelled(): Boolean

    companion object {
        val NONE = NativeFileCancellation { false }
    }
}

internal class NativeFileCancellationSignal : NativeFileCancellation {
    private val cancelled = AtomicBoolean(false)

    override fun isCancelled(): Boolean = cancelled.get()

    fun cancel(): Boolean = cancelled.compareAndSet(false, true)
}

internal fun interface NativeFileProgressListener {
    fun onProgress(bytesCopied: Long, totalBytes: Long)

    companion object {
        val NONE = NativeFileProgressListener { _, _ -> }
    }
}

internal sealed interface NativeFileExecutionResult {
    data class Success(
        val capabilityId: String,
        val bytesAffected: Long,
    ) : NativeFileExecutionResult

    data class Failure(val reason: String) : NativeFileExecutionResult
    data class Cancelled(val bytesAffected: Long) : NativeFileExecutionResult
}

internal interface NativeFilePlatform {
    @Throws(IOException::class)
    fun atomicMove(source: Path, destination: Path, replaceExisting: Boolean)

    @Throws(IOException::class)
    fun delete(path: Path)
}

internal object JavaNativeFilePlatform : NativeFilePlatform {
    override fun atomicMove(source: Path, destination: Path, replaceExisting: Boolean) {
        val options = if (replaceExisting) {
            arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            arrayOf(StandardCopyOption.ATOMIC_MOVE)
        }
        Files.move(source, destination, *options)
    }

    override fun delete(path: Path) = Files.delete(path)
}

/** 执行单文件操作；不递归、不跟随符号链接，也不对非原子移动做静默降级。 */
internal class AndroidNativeFileExecutor(
    private val platform: NativeFilePlatform = JavaNativeFilePlatform,
) {
    fun execute(
        plan: AndroidNativeFilePlan,
        cancellation: NativeFileCancellation = NativeFileCancellation.NONE,
        progress: NativeFileProgressListener = NativeFileProgressListener.NONE,
    ): NativeFileExecutionResult = synchronized(operationLock) {
        if (cancellation.isCancelled()) return@synchronized NativeFileExecutionResult.Cancelled(0L)
        when (plan) {
            is AndroidNativeFilePlan.CopyFile -> copy(plan, cancellation, progress)
            is AndroidNativeFilePlan.MoveFile -> move(plan, cancellation)
            is AndroidNativeFilePlan.DeleteFile -> delete(plan, cancellation)
        }
    }

    private fun copy(
        plan: AndroidNativeFilePlan.CopyFile,
        cancellation: NativeFileCancellation,
        progress: NativeFileProgressListener,
    ): NativeFileExecutionResult {
        validateRegularSource(plan.source)?.let { return it }
        val sourceBytes = plan.source.length()
        if (sourceBytes > plan.maximumBytes) return NativeFileExecutionResult.Failure("native_file_size_limit")
        prepareDestination(plan.destination, plan.replaceExisting, plan.destinationPermissions)?.let { return it }
        if (!deleteTemporary(plan.temporaryFile)) {
            return NativeFileExecutionResult.Failure("native_file_temp_cleanup_failed")
        }
        return try {
            var copied = 0L
            FileInputStream(plan.source).use { input ->
                FileOutputStream(plan.temporaryFile, false).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancellation.isCancelled()) {
                            throw NativeFileCopyCancelled(copied)
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        if (copied > plan.maximumBytes) {
                            deleteTemporary(plan.temporaryFile)
                            return NativeFileExecutionResult.Failure("native_file_size_limit")
                        }
                        output.write(buffer, 0, count)
                        progress.onProgress(copied, sourceBytes)
                    }
                    output.fd.sync()
                }
            }
            if (cancellation.isCancelled()) {
                deleteTemporary(plan.temporaryFile)
                NativeFileExecutionResult.Cancelled(copied)
            } else {
                platform.atomicMove(plan.temporaryFile.toPath(), plan.destination.toPath(), plan.replaceExisting)
                NativeFileExecutionResult.Success(plan.capabilityId, copied)
            }
        } catch (cancelled: NativeFileCopyCancelled) {
            if (deleteTemporary(plan.temporaryFile)) {
                NativeFileExecutionResult.Cancelled(cancelled.bytesAffected)
            } else {
                NativeFileExecutionResult.Failure("native_file_temp_cleanup_failed")
            }
        } catch (error: Throwable) {
            deleteTemporary(plan.temporaryFile)
            failure(error)
        }
    }

    private fun move(
        plan: AndroidNativeFilePlan.MoveFile,
        cancellation: NativeFileCancellation,
    ): NativeFileExecutionResult {
        validateRegularSource(plan.source)?.let { return it }
        prepareDestination(plan.destination, plan.replaceExisting, plan.destinationPermissions)?.let { return it }
        if (cancellation.isCancelled()) return NativeFileExecutionResult.Cancelled(0L)
        return try {
            val bytes = plan.source.length()
            platform.atomicMove(plan.source.toPath(), plan.destination.toPath(), plan.replaceExisting)
            NativeFileExecutionResult.Success(plan.capabilityId, bytes)
        } catch (error: Throwable) {
            failure(error)
        }
    }

    private fun delete(
        plan: AndroidNativeFilePlan.DeleteFile,
        cancellation: NativeFileCancellation,
    ): NativeFileExecutionResult {
        validateRegularSource(plan.target)?.let { return it }
        if (cancellation.isCancelled()) return NativeFileExecutionResult.Cancelled(0L)
        return try {
            val bytes = plan.target.length()
            platform.delete(plan.target.toPath())
            NativeFileExecutionResult.Success(plan.capabilityId, bytes)
        } catch (error: Throwable) {
            failure(error)
        }
    }

    private fun validateRegularSource(source: File): NativeFileExecutionResult.Failure? = when {
        !Files.exists(source.toPath(), LinkOption.NOFOLLOW_LINKS) ->
            NativeFileExecutionResult.Failure("native_file_source_missing")
        Files.isSymbolicLink(source.toPath()) ->
            NativeFileExecutionResult.Failure("native_file_symlink_blocked")
        !Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS) ->
            NativeFileExecutionResult.Failure("native_file_source_not_regular")
        else -> null
    }

    private fun prepareDestination(
        destination: File,
        replaceExisting: Boolean,
        permissions: Set<NativeFilePermission>,
    ): NativeFileExecutionResult.Failure? {
        val parent = destination.parentFile
            ?: return NativeFileExecutionResult.Failure("native_file_destination_invalid")
        if (!parent.mkdirs() && !parent.isDirectory) {
            return NativeFileExecutionResult.Failure("native_file_destination_parent_failed")
        }
        if (Files.isSymbolicLink(destination.toPath())) {
            return NativeFileExecutionResult.Failure("native_file_symlink_blocked")
        }
        if (Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (!replaceExisting) return NativeFileExecutionResult.Failure("native_file_destination_exists")
            if (NativeFilePermission.REPLACE !in permissions) {
                return NativeFileExecutionResult.Failure("native_file_replace_not_authorized")
            }
            if (!Files.isRegularFile(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return NativeFileExecutionResult.Failure("native_file_destination_not_regular")
            }
        }
        return null
    }

    private fun deleteTemporary(file: File): Boolean =
        !Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) || runCatching {
            platform.delete(file.toPath())
        }.isSuccess

    private fun failure(error: Throwable): NativeFileExecutionResult.Failure = when (error) {
        is AtomicMoveNotSupportedException -> NativeFileExecutionResult.Failure("native_file_atomic_move_unsupported")
        is AccessDeniedException, is SecurityException -> NativeFileExecutionResult.Failure("native_file_permission_denied")
        is FileAlreadyExistsException -> NativeFileExecutionResult.Failure("native_file_destination_exists")
        is IOException -> NativeFileExecutionResult.Failure("native_file_io_failure")
        else -> NativeFileExecutionResult.Failure("native_file_failed")
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        val operationLock = Any()
    }
}

private class NativeFileCopyCancelled(val bytesAffected: Long) : IOException()
