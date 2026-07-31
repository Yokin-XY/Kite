package com.kite.app.application.fileprotection

internal enum class FileProtectionPhase {
    Preparing,
    Active,
    Committed,
    RollingBack,
    RolledBack,
    Failed
}

internal enum class FileProtectionEvent {
    Activate,
    OperationSucceeded,
    OperationFailed,
    RestoreRequested,
    RollbackSucceeded,
    RollbackFailed
}

internal object FileProtectionStateMachine {
    fun transition(phase: FileProtectionPhase, event: FileProtectionEvent): FileProtectionPhase =
        when (phase to event) {
            FileProtectionPhase.Preparing to FileProtectionEvent.Activate -> FileProtectionPhase.Active
            FileProtectionPhase.Preparing to FileProtectionEvent.OperationFailed -> FileProtectionPhase.RollingBack
            FileProtectionPhase.Active to FileProtectionEvent.OperationSucceeded -> FileProtectionPhase.Committed
            FileProtectionPhase.Active to FileProtectionEvent.OperationFailed -> FileProtectionPhase.RollingBack
            FileProtectionPhase.Committed to FileProtectionEvent.RestoreRequested -> FileProtectionPhase.RollingBack
            FileProtectionPhase.Failed to FileProtectionEvent.RestoreRequested -> FileProtectionPhase.RollingBack
            FileProtectionPhase.RollingBack to FileProtectionEvent.RollbackSucceeded -> FileProtectionPhase.RolledBack
            FileProtectionPhase.RollingBack to FileProtectionEvent.RollbackFailed -> FileProtectionPhase.Failed
            else -> throw IllegalStateException("invalid file protection transition: $phase + $event")
        }
}

internal enum class FileProtectionBackendId(val wireValue: String) {
    WholeObjectPreimage("whole_object_preimage"),
    RangeUndo("range_undo"),
    MovedInode("moved_inode")
}

internal data class ProtectionScope(val rootHostPath: String)

internal data class ProtectedOperationRequest(
    val ownerId: String,
    val operationKind: String,
    val scope: ProtectionScope,
    val metadata: Map<String, String> = emptyMap(),
    val preferredBackend: FileProtectionBackendId = FileProtectionBackendId.WholeObjectPreimage
)

internal data class FileProtectionCheckpoint(
    val operationId: String,
    val ownerId: String,
    val phase: FileProtectionPhase,
    val committedAt: Long
)

internal object FileProtectionRetentionPolicy {
    fun obsoleteCheckpoints(checkpoints: Collection<FileProtectionCheckpoint>): List<FileProtectionCheckpoint> =
        checkpoints
            .filter { it.phase == FileProtectionPhase.Committed }
            .groupBy(FileProtectionCheckpoint::ownerId)
            .values
            .flatMap { ownerCheckpoints ->
                ownerCheckpoints.sortedByDescending(FileProtectionCheckpoint::committedAt).drop(1)
            }
}

internal class FileProtectionJournalIndex {
    private val paths = linkedSetOf<String>()

    /** true 表示该路径还没有保存过原始事实。 */
    fun claim(relativePath: String): Boolean = paths.add(relativePath)

    fun size(): Int = paths.size
}
