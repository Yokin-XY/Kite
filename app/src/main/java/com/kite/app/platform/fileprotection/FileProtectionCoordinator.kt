package com.kite.app.platform.fileprotection

import com.kite.app.application.fileprotection.FileProtectionBackendId
import com.kite.app.application.fileprotection.FileProtectionCheckpoint
import com.kite.app.application.fileprotection.FileProtectionEvent
import com.kite.app.application.fileprotection.FileProtectionPhase
import com.kite.app.application.fileprotection.FileProtectionRetentionPolicy
import com.kite.app.application.fileprotection.FileProtectionStateMachine
import com.kite.app.application.fileprotection.ProtectedOperationRequest
import com.kite.app.foundation.fileprotection.KiteFileProtectionControl
import com.kite.app.foundation.fileprotection.KiteFileProtectionProtocol
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

internal data class FileProtectionRestoreResult(
    val ownerId: String,
    val operationId: String,
    val metadata: Map<String, String>
)

internal data class FileProtectionRecoveryResult(
    val ownerId: String,
    val restored: Boolean,
    val message: String
)

internal data class FileProtectionRecord(
    val operationId: String,
    val ownerId: String,
    val operationKind: String,
    val rootHostPath: String,
    val journalHostPath: String,
    val backendId: FileProtectionBackendId,
    val phase: FileProtectionPhase,
    val startedAt: Long,
    val committedAt: Long = 0L,
    val lastError: String = "",
    val metadata: Map<String, String> = emptyMap()
)

internal fun interface LegacyFileProtectionRecordDecoder {
    fun decode(properties: Properties): FileProtectionRecord?
}

internal fun interface FileProtectionRestoreGuard {
    fun quiesce(record: FileProtectionRecord): Result<Unit>
}

/**
 * 业务无关的文件保护状态拥有者。
 *
 * 它只理解 owner、operation、scope、journal 和 backend；资源、更新按钮与版本语义由适配器提供。
 */
internal class FileProtectionCoordinator(
    private val storeRoot: File,
    private val controlFile: File,
    backends: Collection<FileProtectionStorageBackend> = listOf(
        WholeObjectPreimageBackend(),
        RangeUndoBackend()
    ),
    private val legacyRecordDecoders: List<LegacyFileProtectionRecordDecoder> = emptyList(),
    private val restoreGuard: FileProtectionRestoreGuard = FileProtectionRestoreGuard { Result.success(Unit) },
    private val now: () -> Long = System::currentTimeMillis,
    private val maxJournalBytes: Long = DEFAULT_MAX_JOURNAL_BYTES,
    private val nativePath: (File) -> String = File::getAbsolutePath
) {
    private data class StoredRecord(val root: File, val record: FileProtectionRecord)

    private val lock = Any()
    private val backendsById = backends.associateBy(FileProtectionStorageBackend::id)
    private val checkpointOwners = linkedSetOf<String>()

    init {
        require(backendsById.isNotEmpty()) { "file protection requires at least one backend" }
        synchronized(lock) { refreshCheckpointCacheLocked() }
    }

    fun begin(request: ProtectedOperationRequest): Result<String> = synchronized(lock) {
        runCatching {
            recoverInterruptedLocked().firstOrNull { !it.restored }?.let { failure ->
                error("interrupted_file_protection_recovery_failed:${failure.message}")
            }
            if (controlFile.exists()) error("file_protection_busy")
            val ownerId = requireSafeId(request.ownerId, "owner")
            val operationKind = requireSafeId(request.operationKind, "operation kind")
            val root = File(request.scope.rootHostPath).toPath().toAbsolutePath().normalize()
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) error("file_protection_scope_missing")
            val backend = backendsById[request.preferredBackend]
                ?: error("file_protection_backend_unavailable:${request.preferredBackend.wireValue}")
            val cleanMetadata = validateMetadata(request.metadata)
            val operationId = "$ownerId-${now()}-${UUID.randomUUID().toString().replace("-", "")}"
            val operationRoot = File(File(storeRoot, ownerId), operationId)
            val journalRoot = File(operationRoot, JOURNAL_DIR_NAME)
            if (!journalRoot.mkdirs() && !journalRoot.isDirectory) error("file_protection_journal_create_failed")
            val preparing = FileProtectionRecord(
                operationId = operationId,
                ownerId = ownerId,
                operationKind = operationKind,
                rootHostPath = root.toString(),
                journalHostPath = journalRoot.absolutePath,
                backendId = backend.id,
                phase = FileProtectionPhase.Preparing,
                startedAt = now(),
                metadata = cleanMetadata
            )
            writeRecord(operationRoot, preparing)
            try {
                val control = KiteFileProtectionControl(
                    generation = nextGenerationLocked(),
                    operationId = operationId,
                    rootHostPath = nativePath(root.toFile()),
                    journalHostPath = nativePath(journalRoot),
                    maxJournalBytes = maxJournalBytes,
                    backendId = backend.id
                )
                atomicWriteText(controlFile, KiteFileProtectionProtocol.encodeControl(control))
                writeRecord(
                    operationRoot,
                    preparing.copy(
                        phase = FileProtectionStateMachine.transition(
                            preparing.phase,
                            FileProtectionEvent.Activate
                        )
                    )
                )
                operationId
            } catch (error: Throwable) {
                controlFile.delete()
                operationRoot.deleteRecursively()
                throw error
            }
        }
    }

    fun commit(ownerId: String): Result<Unit> = synchronized(lock) {
        runCatching {
            val active = activeRecord(ownerId) ?: error("active_file_protection_operation_missing")
            deactivateControlLocked()
            val committed = active.record.copy(
                phase = FileProtectionStateMachine.transition(
                    active.record.phase,
                    FileProtectionEvent.OperationSucceeded
                ),
                committedAt = now(),
                lastError = ""
            )
            writeRecord(active.root, committed)
            removeObsoleteCommittedLocked(committed.ownerId, committed.operationId)
            refreshCheckpointCacheLocked()
        }
    }

    fun rollback(ownerId: String): Result<FileProtectionRestoreResult> = synchronized(lock) {
        val active = activeRecord(ownerId)
            ?: return@synchronized Result.success(FileProtectionRestoreResult(ownerId, "", emptyMap()))
        runCatching {
            deactivateControlLocked()
            rollbackLocked(active.root, active.record, FileProtectionEvent.OperationFailed)
        }
    }

    fun restoreLatest(ownerId: String): Result<FileProtectionRestoreResult> = synchronized(lock) {
        runCatching {
            recoverInterruptedLocked().firstOrNull { !it.restored }?.let { failure ->
                error("interrupted_file_protection_recovery_failed:${failure.message}")
            }
            val cleanOwnerId = requireSafeId(ownerId, "owner")
            val checkpoint = recordsLocked()
                .filter { it.record.ownerId == cleanOwnerId && it.record.phase in RESTORABLE_PHASES }
                .maxByOrNull { it.record.committedAt.coerceAtLeast(it.record.startedAt) }
                ?: error("file_protection_checkpoint_missing")
            rollbackLocked(checkpoint.root, checkpoint.record, FileProtectionEvent.RestoreRequested)
        }
    }

    fun hasCheckpoint(ownerId: String): Boolean = synchronized(lock) {
        ownerId in checkpointOwners
    }

    fun deactivateInterruptedOperation() = synchronized(lock) {
        if (controlFile.exists()) deactivateControlLocked()
    }

    fun recoverInterruptedOperations(): List<FileProtectionRecoveryResult> = synchronized(lock) {
        recoverInterruptedLocked()
    }

    /**
     * 将旧版可长期恢复的保护记录收口为一次性事务：中断或回滚失败就自动重试，
     * 已提交记录直接清理，不再保留给以后手动恢复。
     */
    fun settleTransientOperations(): List<FileProtectionRecoveryResult> = synchronized(lock) {
        val results = recoverInterruptedLocked().toMutableList()
        recordsLocked()
            .filter { it.record.phase == FileProtectionPhase.Failed }
            .forEach { stored ->
                results += runCatching {
                    rollbackLocked(stored.root, stored.record, FileProtectionEvent.RestoreRequested)
                }.fold(
                    onSuccess = {
                        FileProtectionRecoveryResult(
                            ownerId = stored.record.ownerId,
                            restored = true,
                            message = "上次自动回滚未完成，本次启动已恢复操作前状态"
                        )
                    },
                    onFailure = { error ->
                        FileProtectionRecoveryResult(
                            ownerId = stored.record.ownerId,
                            restored = false,
                            message = error.message ?: error.javaClass.simpleName
                        )
                    }
                )
            }
        recordsLocked()
            .filter { it.record.phase == FileProtectionPhase.Committed }
            .forEach { it.root.deleteRecursively() }
        refreshCheckpointCacheLocked()
        results
    }

    private fun recoverInterruptedLocked(): List<FileProtectionRecoveryResult> {
        if (controlFile.exists()) deactivateControlLocked()
        return recordsLocked()
            .filter { it.record.phase in INTERRUPTED_PHASES }
            .map { stored ->
                runCatching {
                    rollbackLocked(stored.root, stored.record, FileProtectionEvent.OperationFailed)
                }.fold(
                    onSuccess = {
                        FileProtectionRecoveryResult(
                            ownerId = stored.record.ownerId,
                            restored = true,
                            message = "上次受保护操作中断，已自动恢复操作前状态"
                        )
                    },
                    onFailure = { error ->
                        FileProtectionRecoveryResult(
                            ownerId = stored.record.ownerId,
                            restored = false,
                            message = error.message ?: error.javaClass.simpleName
                        )
                    }
                )
            }
    }

    private fun rollbackLocked(
        operationRoot: File,
        source: FileProtectionRecord,
        event: FileProtectionEvent
    ): FileProtectionRestoreResult {
        val rollingBack = source.copy(
            phase = when (source.phase) {
                FileProtectionPhase.RollingBack -> FileProtectionPhase.RollingBack
                else -> FileProtectionStateMachine.transition(source.phase, event)
            },
            lastError = ""
        )
        writeRecord(operationRoot, rollingBack)
        return try {
            restoreGuard.quiesce(rollingBack).getOrThrow()
            val backend = backendsById[rollingBack.backendId]
                ?: error("file_protection_backend_unavailable:${rollingBack.backendId.wireValue}")
            backend.restore(File(rollingBack.rootHostPath), File(rollingBack.journalHostPath)).getOrThrow()
            val rolledBack = rollingBack.copy(
                phase = FileProtectionStateMachine.transition(
                    rollingBack.phase,
                    FileProtectionEvent.RollbackSucceeded
                )
            )
            writeRecord(operationRoot, rolledBack)
            refreshCheckpointCacheLocked()
            FileProtectionRestoreResult(
                ownerId = rolledBack.ownerId,
                operationId = rolledBack.operationId,
                metadata = rolledBack.metadata
            )
        } catch (error: Throwable) {
            writeRecord(
                operationRoot,
                rollingBack.copy(
                    phase = FileProtectionStateMachine.transition(
                        rollingBack.phase,
                        FileProtectionEvent.RollbackFailed
                    ),
                    lastError = error.message ?: error.javaClass.simpleName
                )
            )
            refreshCheckpointCacheLocked()
            throw error
        }
    }

    private fun activeRecord(ownerId: String): StoredRecord? {
        val cleanOwnerId = requireSafeId(ownerId, "owner")
        return recordsLocked().firstOrNull {
            it.record.ownerId == cleanOwnerId && it.record.phase == FileProtectionPhase.Active
        }
    }

    private fun recordsLocked(): List<StoredRecord> = storeRoot.listFiles().orEmpty()
        .filter(File::isDirectory)
        .flatMap { ownerRoot -> ownerRoot.listFiles().orEmpty().filter(File::isDirectory).toList() }
        .mapNotNull { operationRoot -> readRecord(operationRoot)?.let { StoredRecord(operationRoot, it) } }

    private fun removeObsoleteCommittedLocked(ownerId: String, retainedOperationId: String) {
        val checkpoints = recordsLocked().map { stored ->
            FileProtectionCheckpoint(
                operationId = stored.record.operationId,
                ownerId = stored.record.ownerId,
                phase = stored.record.phase,
                committedAt = stored.record.committedAt
            )
        }
        val obsoleteIds = FileProtectionRetentionPolicy.obsoleteCheckpoints(checkpoints)
            .filter { it.ownerId == ownerId && it.operationId != retainedOperationId }
            .mapTo(hashSetOf(), FileProtectionCheckpoint::operationId)
        recordsLocked().filter { it.record.operationId in obsoleteIds }.forEach { it.root.deleteRecursively() }
    }

    private fun refreshCheckpointCacheLocked() {
        checkpointOwners.clear()
        recordsLocked()
            .filter { it.record.phase in RESTORABLE_PHASES }
            .mapTo(checkpointOwners) { it.record.ownerId }
    }

    private fun nextGenerationLocked(): Long {
        val file = File(storeRoot, GENERATION_FILE_NAME)
        val next = (file.takeIf(File::isFile)?.readText()?.trim()?.toLongOrNull() ?: 0L) + 1L
        atomicWriteText(file, "$next\n")
        return next
    }

    private fun deactivateControlLocked() {
        if (controlFile.exists() && !controlFile.delete()) error("file_protection_control_deactivate_failed")
    }

    private fun writeRecord(operationRoot: File, record: FileProtectionRecord) {
        val properties = Properties().apply {
            setProperty("schema", RECORD_SCHEMA)
            setProperty("operationId", record.operationId)
            setProperty("ownerId", record.ownerId)
            setProperty("operationKind", record.operationKind)
            setProperty("rootHostPath", record.rootHostPath)
            setProperty("journalHostPath", record.journalHostPath)
            setProperty("backendId", record.backendId.wireValue)
            setProperty("phase", record.phase.name)
            setProperty("startedAt", record.startedAt.toString())
            setProperty("committedAt", record.committedAt.toString())
            setProperty("lastError", record.lastError)
            record.metadata.toSortedMap().forEach { (key, value) -> setProperty("metadata.$key", value) }
        }
        operationRoot.mkdirs()
        atomicWriteProperties(File(operationRoot, RECORD_FILE_NAME), properties)
    }

    private fun readRecord(operationRoot: File): FileProtectionRecord? = runCatching {
        val file = File(operationRoot, RECORD_FILE_NAME)
        if (!file.isFile) return@runCatching null
        val properties = Properties().apply { FileInputStream(file).use(::load) }
        if (properties.getProperty("schema") != RECORD_SCHEMA) {
            return@runCatching legacyRecordDecoders.firstNotNullOfOrNull { it.decode(properties) }
        }
        val metadata = properties.stringPropertyNames()
            .filter { it.startsWith(METADATA_PREFIX) }
            .associate { key -> key.removePrefix(METADATA_PREFIX) to properties.getProperty(key).orEmpty() }
        FileProtectionRecord(
            operationId = properties.getProperty("operationId"),
            ownerId = properties.getProperty("ownerId"),
            operationKind = properties.getProperty("operationKind"),
            rootHostPath = properties.getProperty("rootHostPath"),
            journalHostPath = properties.getProperty("journalHostPath"),
            backendId = properties.getProperty("backendId")
                ?.let { wire -> FileProtectionBackendId.entries.firstOrNull { it.wireValue == wire } }
                ?: FileProtectionBackendId.WholeObjectPreimage,
            phase = FileProtectionPhase.valueOf(properties.getProperty("phase")),
            startedAt = properties.getProperty("startedAt").toLong(),
            committedAt = properties.getProperty("committedAt").toLong(),
            lastError = properties.getProperty("lastError").orEmpty(),
            metadata = metadata
        )
    }.getOrNull()

    private fun validateMetadata(metadata: Map<String, String>): Map<String, String> {
        require(metadata.size <= 32) { "file protection metadata too large" }
        return metadata.toSortedMap().onEach { (key, value) ->
            require(METADATA_KEY_PATTERN.matches(key)) { "unsafe file protection metadata key" }
            require(value.length <= 4096 && '\u0000' !in value && '\n' !in value && '\r' !in value) {
                "unsafe file protection metadata value"
            }
        }
    }

    private fun requireSafeId(value: String, label: String): String {
        val clean = value.trim()
        require(SAFE_ID_PATTERN.matches(clean)) { "unsafe file protection $label" }
        return clean
    }

    private fun atomicWriteText(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        atomicReplace(temp, target)
    }

    private fun atomicWriteProperties(target: File, properties: Properties) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
        FileOutputStream(temp).use { output ->
            properties.store(output, null)
            output.fd.sync()
        }
        atomicReplace(temp, target)
    }

    private fun atomicReplace(temp: File, target: File) {
        runCatching {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val RECORD_SCHEMA = "kf_file_protection_record_v1"
        private const val RECORD_FILE_NAME = "record.properties"
        private const val JOURNAL_DIR_NAME = "entries"
        private const val GENERATION_FILE_NAME = "generation"
        private const val METADATA_PREFIX = "metadata."
        private const val DEFAULT_MAX_JOURNAL_BYTES = 512L * 1024L * 1024L
        private val SAFE_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        private val METADATA_KEY_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
        private val INTERRUPTED_PHASES = setOf(
            FileProtectionPhase.Preparing,
            FileProtectionPhase.Active,
            FileProtectionPhase.RollingBack
        )
        private val RESTORABLE_PHASES = setOf(FileProtectionPhase.Committed, FileProtectionPhase.Failed)
    }
}
