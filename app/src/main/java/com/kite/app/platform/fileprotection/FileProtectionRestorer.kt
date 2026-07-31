package com.kite.app.platform.fileprotection

import com.kite.app.foundation.fileprotection.KiteFileProtectionBeforeKind
import com.kite.app.foundation.fileprotection.KiteFileProtectionEntry
import com.kite.app.foundation.fileprotection.KiteFileProtectionProtocol
import com.kite.app.foundation.fileprotection.KiteFileProtectionStorageMode
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission

internal data class FileProtectionSnapshot(
    val entry: KiteFileProtectionEntry,
    val payload: Path?,
    val ranges: List<FileProtectionRangeSnapshot> = emptyList()
)

internal data class FileProtectionRangeSnapshot(
    val offset: Long,
    val length: Long,
    val payload: Path
)

internal class FileProtectionJournalReader {
    fun read(journalRoot: File): Result<List<FileProtectionSnapshot>> = runCatching {
        if (!journalRoot.isDirectory) error("file_protection_journal_missing")
        val snapshots = journalRoot.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".tmp-") }
            .sortedBy(File::getName)
            .map { entryDir ->
                val meta = File(entryDir, META_FILE_NAME)
                val entry = meta.takeIf(File::isFile)
                    ?.readText(Charsets.UTF_8)
                    ?.let(KiteFileProtectionProtocol::decodeEntry)
                    ?: error("file_protection_entry_incomplete:${entryDir.name}")
                val payload = File(entryDir, PAYLOAD_NAME).toPath()
                if (
                    entry.storageMode == KiteFileProtectionStorageMode.WholeObject &&
                    entry.beforeKind in PAYLOAD_KINDS &&
                    !Files.exists(payload, LinkOption.NOFOLLOW_LINKS)
                ) {
                    error("file_protection_payload_missing:${entryDir.name}")
                }
                val ranges = if (entry.storageMode == KiteFileProtectionStorageMode.RangeUndo) {
                    readRanges(entryDir)
                } else {
                    emptyList()
                }
                FileProtectionSnapshot(
                    entry = entry,
                    payload = payload.takeIf { Files.exists(it, LinkOption.NOFOLLOW_LINKS) },
                    ranges = ranges
                )
            }
        val duplicate = snapshots.groupBy { it.entry.relativePath }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) error("file_protection_entry_duplicate:${duplicate.key}")
        snapshots
    }

    companion object {
        const val META_FILE_NAME = "meta"
        const val PAYLOAD_NAME = "payload"
        private val PAYLOAD_KINDS = setOf(
            KiteFileProtectionBeforeKind.File,
            KiteFileProtectionBeforeKind.Directory
        )
        private val RANGE_FILE_PATTERN = Regex("([0-9a-fA-F]{16})-([0-9a-fA-F]{16})")
        private const val RANGES_DIR_NAME = "ranges"
    }

    private fun readRanges(entryDir: File): List<FileProtectionRangeSnapshot> {
        val rangesDir = File(entryDir, RANGES_DIR_NAME)
        if (!rangesDir.exists()) return emptyList()
        if (!rangesDir.isDirectory) error("file_protection_ranges_invalid:${entryDir.name}")
        val ranges = rangesDir.listFiles().orEmpty()
            .filter(File::isFile)
            .map { file ->
                val match = RANGE_FILE_PATTERN.matchEntire(file.name)
                    ?: error("file_protection_range_name_invalid:${file.name}")
                val offset = match.groupValues[1].toULong(16).toLong()
                val length = match.groupValues[2].toULong(16).toLong()
                if (offset < 0L || length <= 0L || file.length() != length) {
                    error("file_protection_range_invalid:${file.name}")
                }
                FileProtectionRangeSnapshot(offset, length, file.toPath())
            }
            .sortedBy(FileProtectionRangeSnapshot::offset)
        ranges.zipWithNext().firstOrNull { (left, right) -> left.offset + left.length > right.offset }?.let {
            error("file_protection_ranges_overlap:${entryDir.name}")
        }
        return ranges
    }
}

internal sealed interface FileProtectionRollbackOperation {
    val relativePath: String

    data class DeleteCurrent(override val relativePath: String) : FileProtectionRollbackOperation

    data class RestoreBefore(val entry: KiteFileProtectionEntry) : FileProtectionRollbackOperation {
        override val relativePath: String = entry.relativePath
    }
}

internal object FileProtectionRollbackPlanner {
    fun plan(entries: Collection<KiteFileProtectionEntry>): List<FileProtectionRollbackOperation> {
        val unique = entries.distinctBy(KiteFileProtectionEntry::relativePath)
        val deleteCurrent = unique
            .filter { it.storageMode == KiteFileProtectionStorageMode.WholeObject }
            .sortedWith(compareByDescending<KiteFileProtectionEntry> { it.depth }.thenByDescending { it.relativePath })
            .map { FileProtectionRollbackOperation.DeleteCurrent(it.relativePath) }
        val restoreBefore = unique
            .filter { it.beforeKind != KiteFileProtectionBeforeKind.Absent }
            .sortedWith(compareBy<KiteFileProtectionEntry> { it.depth }.thenBy { it.relativePath })
            .map { FileProtectionRollbackOperation.RestoreBefore(it) }
        return deleteCurrent + restoreBefore
    }
}

internal interface FileProtectionRestoreOps {
    fun deleteCurrent(path: Path)
    fun restoreBefore(snapshot: FileProtectionSnapshot, target: Path)
}

internal class FileProtectionRestorer(
    private val ops: FileProtectionRestoreOps = NioFileProtectionRestoreOps()
) {
    fun restore(root: File, snapshots: Collection<FileProtectionSnapshot>): Result<Unit> = runCatching {
        val rootPath = root.toPath().toAbsolutePath().normalize()
        val byPath = snapshots.associateBy { it.entry.relativePath }
        FileProtectionRollbackPlanner.plan(snapshots.map(FileProtectionSnapshot::entry)).forEach { operation ->
            val target = resolveInside(rootPath, operation.relativePath)
            when (operation) {
                is FileProtectionRollbackOperation.DeleteCurrent -> ops.deleteCurrent(target)
                is FileProtectionRollbackOperation.RestoreBefore -> ops.restoreBefore(
                    snapshot = byPath.getValue(operation.relativePath),
                    target = target
                )
            }
        }
    }

    private fun resolveInside(root: Path, relativePath: String): Path {
        val normalized = KiteFileProtectionProtocol.normalizeRelativePath(relativePath)
            ?: error("unsafe_file_protection_path")
        val target = if (normalized == ".") root else root.resolve(normalized).normalize()
        if (target != root && !target.startsWith(root)) error("file_protection_path_escaped")
        return target
    }
}

internal class NioFileProtectionRestoreOps : FileProtectionRestoreOps {
    override fun deleteCurrent(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            Files.deleteIfExists(path)
        }
    }

    override fun restoreBefore(snapshot: FileProtectionSnapshot, target: Path) {
        target.parent?.let(Files::createDirectories)
        when (snapshot.entry.beforeKind) {
            KiteFileProtectionBeforeKind.Absent -> Unit
            KiteFileProtectionBeforeKind.File -> {
                if (snapshot.entry.storageMode == KiteFileProtectionStorageMode.RangeUndo) {
                    restoreRanges(snapshot, target)
                    applyMode(target, snapshot.entry.mode)
                    return
                }
                Files.copy(requireNotNull(snapshot.payload), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                applyMode(target, snapshot.entry.mode)
            }
            KiteFileProtectionBeforeKind.Directory -> {
                copyDirectory(requireNotNull(snapshot.payload), target)
                applyMode(target, snapshot.entry.mode)
            }
            KiteFileProtectionBeforeKind.Symlink -> {
                Files.createSymbolicLink(target, Paths.get(snapshot.entry.linkTarget))
            }
        }
    }

    private fun restoreRanges(snapshot: FileProtectionSnapshot, target: Path) {
        snapshot.payload?.let { fallback ->
            deleteCurrent(target)
            Files.copy(fallback, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            error("file_protection_range_target_missing:${snapshot.entry.relativePath}")
        }
        RandomAccessFile(target.toFile(), "rw").use { output ->
            snapshot.ranges.forEach { range ->
                output.seek(range.offset)
                Files.newInputStream(range.payload).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var remaining = range.length
                    while (remaining > 0L) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count <= 0) error("file_protection_range_payload_short:${range.payload.fileName}")
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                }
            }
            output.setLength(snapshot.entry.originalSize)
            output.fd.sync()
        }
    }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destination = target.resolve(source.relativize(dir))
                Files.createDirectories(destination)
                copyPosixMode(dir, destination)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destination = target.resolve(source.relativize(file))
                if (Files.isSymbolicLink(file)) {
                    Files.createSymbolicLink(destination, Files.readSymbolicLink(file))
                } else {
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun applyMode(path: Path, mode: Int) {
        val permissions = linkedSetOf<PosixFilePermission>().apply {
            if (mode and 0x100 != 0) add(PosixFilePermission.OWNER_READ)
            if (mode and 0x080 != 0) add(PosixFilePermission.OWNER_WRITE)
            if (mode and 0x040 != 0) add(PosixFilePermission.OWNER_EXECUTE)
            if (mode and 0x020 != 0) add(PosixFilePermission.GROUP_READ)
            if (mode and 0x010 != 0) add(PosixFilePermission.GROUP_WRITE)
            if (mode and 0x008 != 0) add(PosixFilePermission.GROUP_EXECUTE)
            if (mode and 0x004 != 0) add(PosixFilePermission.OTHERS_READ)
            if (mode and 0x002 != 0) add(PosixFilePermission.OTHERS_WRITE)
            if (mode and 0x001 != 0) add(PosixFilePermission.OTHERS_EXECUTE)
        }
        runCatching { Files.setPosixFilePermissions(path, permissions) }
    }

    private fun copyPosixMode(source: Path, target: Path) {
        runCatching { Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source)) }
    }
}
