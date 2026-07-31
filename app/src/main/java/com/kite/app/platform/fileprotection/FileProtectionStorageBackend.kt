package com.kite.app.platform.fileprotection

import com.kite.app.application.fileprotection.FileProtectionBackendId
import java.io.File

internal interface FileProtectionStorageBackend {
    val id: FileProtectionBackendId
    fun restore(scopeRoot: File, journalRoot: File): Result<Unit>
}

internal class WholeObjectPreimageBackend(
    private val journalReader: FileProtectionJournalReader = FileProtectionJournalReader(),
    private val restorer: FileProtectionRestorer = FileProtectionRestorer()
) : FileProtectionStorageBackend {
    override val id: FileProtectionBackendId = FileProtectionBackendId.WholeObjectPreimage

    override fun restore(scopeRoot: File, journalRoot: File): Result<Unit> = runCatching {
        val snapshots = journalReader.read(journalRoot).getOrThrow()
        restorer.restore(scopeRoot, snapshots).getOrThrow()
    }
}

/**
 * 区间撤销日志后端。
 *
 * PRoot 负责在写入前保存被覆盖区间，并在无法闭包的 syscall 上写入整对象保底；
 * Android 只消费统一 journal，不需要理解产生这些记录的具体业务动作。
 */
internal class RangeUndoBackend(
    private val journalReader: FileProtectionJournalReader = FileProtectionJournalReader(),
    private val restorer: FileProtectionRestorer = FileProtectionRestorer()
) : FileProtectionStorageBackend {
    override val id: FileProtectionBackendId = FileProtectionBackendId.RangeUndo

    override fun restore(scopeRoot: File, journalRoot: File): Result<Unit> = runCatching {
        val snapshots = journalReader.read(journalRoot).getOrThrow()
        restorer.restore(scopeRoot, snapshots).getOrThrow()
    }
}
