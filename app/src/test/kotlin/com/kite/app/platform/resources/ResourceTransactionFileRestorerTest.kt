package com.kite.app.platform.fileprotection

import com.kite.app.foundation.fileprotection.KiteFileProtectionBeforeKind
import com.kite.app.foundation.fileprotection.KiteFileProtectionEntry
import com.kite.app.foundation.fileprotection.KiteFileProtectionProtocol
import com.kite.app.foundation.fileprotection.KiteFileProtectionStorageMode
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

class FileProtectionRestorerTest {
    @Test
    fun `区间日志叠加升级时的当前副本可恢复原文件`() {
        val temp = Files.createTempDirectory("kite-file-protection-range")
        try {
            val root = Files.createDirectories(temp.resolve("root"))
            val entries = Files.createDirectories(temp.resolve("entries"))
            val target = root.resolve("large.bin")
            val original = ByteArray(64 * 1024) { index -> (index and 0xff).toByte() }
            Files.write(target, original)

            val entryDir = Files.createDirectories(entries.resolve("range-entry"))
            val ranges = Files.createDirectories(entryDir.resolve("ranges"))
            val oldRange = original.copyOfRange(4096, 8192)
            Files.write(ranges.resolve("0000000000001000-0000000000001000"), oldRange)
            RandomAccessFile(target.toFile(), "rw").use { file ->
                file.seek(4096)
                file.write(ByteArray(4096) { 0x5a })
            }
            Files.copy(target, entryDir.resolve("payload"))
            Files.delete(target)
            Files.createDirectory(target)
            writeText(target.resolve("replacement.txt"), "different node type")
            writeText(
                entryDir.resolve("meta"),
                KiteFileProtectionProtocol.encodeEntry(
                    KiteFileProtectionEntry(
                        relativePath = "large.bin",
                        beforeKind = KiteFileProtectionBeforeKind.File,
                        mode = 0x1A4,
                        storageMode = KiteFileProtectionStorageMode.RangeUndo,
                        originalSize = original.size.toLong()
                    )
                )
            )

            val snapshots = FileProtectionJournalReader().read(entries.toFile()).getOrThrow()
            FileProtectionRestorer().restore(root.toFile(), snapshots).getOrThrow()

            assertTrue(Files.readAllBytes(target).contentEquals(original))
        } finally {
            NioFileProtectionRestoreOps().deleteCurrent(temp)
        }
    }

    @Test
    fun `真机 PRoot journal 可由同一恢复器直接消费`() {
        val journalPath = System.getenv("KITE_NATIVE_TXN_JOURNAL")
        assumeNotNull(journalPath)
        val journal = Paths.get(requireNotNull(journalPath))
        val snapshots = FileProtectionJournalReader().read(journal.toFile()).getOrThrow()
        val byPath = snapshots.associateBy { it.entry.relativePath }

        assertEquals(15, snapshots.size)
        assertEquals(KiteFileProtectionBeforeKind.File, byPath.getValue("existing.txt").entry.beforeKind)
        assertEquals(KiteFileProtectionBeforeKind.Directory, byPath.getValue("directory-source").entry.beforeKind)
        assertEquals(KiteFileProtectionBeforeKind.Absent, byPath.getValue("hard-link.txt").entry.beforeKind)
        assertEquals(KiteFileProtectionBeforeKind.Absent, byPath.getValue(".l2s.existing.txt0001").entry.beforeKind)

        val temp = Files.createTempDirectory("kite-native-resource-txn-restore")
        try {
            val current = Files.createDirectories(temp.resolve("current"))
            listOf(
                "existing.txt",
                "delete-me.txt",
                "rename-source.txt",
                "rename-destination.txt",
                "truncate.txt",
                "mode.txt",
                "concurrent.txt",
                "hard-link.txt",
                "symbolic-link.txt",
                ".l2s.existing.txt0001",
                ".l2s.existing.txt0001.0002"
            ).forEach { path -> writeText(current.resolve(path), "mutated") }
            Files.createDirectories(current.resolve("created-directory"))
            writeText(current.resolve("created-directory/new.txt"), "created")
            Files.createDirectories(current.resolve("directory-renamed"))
            Files.createDirectories(current.resolve("directory-source"))
            writeText(current.resolve("directory-source/child.txt"), "mutated-child")

            FileProtectionRestorer().restore(current.toFile(), snapshots).getOrThrow()

            assertEquals("old-existing", readText(current.resolve("existing.txt")))
            assertEquals("old-delete", readText(current.resolve("delete-me.txt")))
            assertEquals("old-source", readText(current.resolve("rename-source.txt")))
            assertEquals("old-destination", readText(current.resolve("rename-destination.txt")))
            assertEquals("old-child", readText(current.resolve("directory-source/child.txt")))
            assertFalse(Files.exists(current.resolve("created-directory")))
            assertFalse(Files.exists(current.resolve("directory-renamed")))
            assertFalse(Files.exists(current.resolve("hard-link.txt")))
            assertFalse(Files.exists(current.resolve(".l2s.existing.txt0001")))
        } finally {
            NioFileProtectionRestoreOps().deleteCurrent(temp)
        }
    }

    @Test
    fun `真实文件和目录恢复且更新中新建路径被删除`() {
        val temp = Files.createTempDirectory("kite-resource-txn-test")
        try {
            val root = Files.createDirectories(temp.resolve("root"))
            val entries = Files.createDirectories(temp.resolve("entries"))
            writeSnapshot(entries, "001", entry("bin/opencode", KiteFileProtectionBeforeKind.File, 0x1ED), "old-binary")
            writeDirectorySnapshot(entries, "002", entry("embedded", KiteFileProtectionBeforeKind.Directory, 0x1C0))
            writeSnapshot(entries, "003", entry("generated/cache", KiteFileProtectionBeforeKind.Absent, 0), null)

            Files.createDirectories(root.resolve("bin"))
            writeText(root.resolve("bin/opencode"), "broken-new-binary")
            Files.createDirectories(root.resolve("embedded"))
            writeText(root.resolve("embedded/state.txt"), "mutated-state")
            Files.createDirectories(root.resolve("generated"))
            writeText(root.resolve("generated/cache"), "new-cache")

            val snapshots = FileProtectionJournalReader().read(entries.toFile()).getOrThrow()
            FileProtectionRestorer().restore(root.toFile(), snapshots).getOrThrow()

            assertEquals("old-binary", readText(root.resolve("bin/opencode")))
            assertEquals("old-state", readText(root.resolve("embedded/state.txt")))
            assertFalse(Files.exists(root.resolve("generated/cache")))
        } finally {
            NioFileProtectionRestoreOps().deleteCurrent(temp)
        }
    }

    @Test
    fun `符号链接和权限事实按合同交给恢复后端`() {
        val root = Paths.get("/virtual/resource-root")
        val snapshots = listOf(
            FileProtectionSnapshot(
                entry("current", KiteFileProtectionBeforeKind.Symlink, 0x1FF, "versions/v1"),
                payload = null
            ),
            FileProtectionSnapshot(
                entry("bin/tool", KiteFileProtectionBeforeKind.File, 0x1ED),
                payload = Paths.get("/virtual/payload")
            )
        )
        val ops = RecordingOps()

        FileProtectionRestorer(ops).restore(root.toFile(), snapshots).getOrThrow()

        assertEquals(listOf("bin/tool", "current"), ops.deleted)
        assertEquals(
            listOf(
                Triple("current", KiteFileProtectionBeforeKind.Symlink, 0x1FF),
                Triple("bin/tool", KiteFileProtectionBeforeKind.File, 0x1ED)
            ),
            ops.restored
        )
        assertEquals("versions/v1", snapshots.first().entry.linkTarget)
    }

    @Test
    fun `不完整 entry 会阻止回滚而不是静默跳过`() {
        val temp = Files.createTempDirectory("kite-resource-txn-incomplete")
        try {
            Files.createDirectories(temp.resolve("entry"))
            writeText(temp.resolve("entry/meta"), "capture_state=copying\n")

            val result = FileProtectionJournalReader().read(temp.toFile())

            assertTrue(result.isFailure)
        } finally {
            NioFileProtectionRestoreOps().deleteCurrent(temp)
        }
    }

    private fun writeSnapshot(
        entries: Path,
        id: String,
        entry: KiteFileProtectionEntry,
        payload: String?
    ) {
        val dir = Files.createDirectories(entries.resolve(id))
        writeText(dir.resolve("meta"), KiteFileProtectionProtocol.encodeEntry(entry))
        if (payload != null) writeText(dir.resolve("payload"), payload)
    }

    private fun writeDirectorySnapshot(
        entries: Path,
        id: String,
        entry: KiteFileProtectionEntry
    ) {
        val dir = Files.createDirectories(entries.resolve(id))
        writeText(dir.resolve("meta"), KiteFileProtectionProtocol.encodeEntry(entry))
        val payload = Files.createDirectories(dir.resolve("payload"))
        writeText(payload.resolve("state.txt"), "old-state")
    }

    private fun entry(
        path: String,
        kind: KiteFileProtectionBeforeKind,
        mode: Int,
        linkTarget: String = ""
    ) = KiteFileProtectionEntry(path, kind, mode, linkTarget)

    private fun writeText(path: Path, value: String) {
        Files.write(path, value.toByteArray(Charsets.UTF_8))
    }

    private fun readText(path: Path): String = Files.readAllBytes(path).toString(Charsets.UTF_8)

    private class RecordingOps : FileProtectionRestoreOps {
        val deleted = mutableListOf<String>()
        val restored = mutableListOf<Triple<String, KiteFileProtectionBeforeKind, Int>>()

        override fun deleteCurrent(path: Path) {
            deleted += path.fileName.toString().let { name ->
                if (name == "tool") "bin/tool" else name
            }
        }

        override fun restoreBefore(snapshot: FileProtectionSnapshot, target: Path) {
            restored += Triple(snapshot.entry.relativePath, snapshot.entry.beforeKind, snapshot.entry.mode)
        }
    }
}
