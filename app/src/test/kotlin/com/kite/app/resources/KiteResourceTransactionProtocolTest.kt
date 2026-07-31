package com.kite.app.foundation.fileprotection

import com.kite.app.application.fileprotection.FileProtectionBackendId

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteFileProtectionProtocolTest {
    @Test
    fun `active 控制记录可往返且拒绝越界输入`() {
        val control = KiteFileProtectionControl(
            generation = 7,
            operationId = "txn-op-7",
            rootHostPath = "/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/.kf/software/kite.example",
            journalHostPath = "/data/user/0/com.kite.app/files/resource-transactions/txn-op-7/entries",
            maxJournalBytes = 64L * 1024L * 1024L,
            backendId = FileProtectionBackendId.WholeObjectPreimage
        )

        assertEquals(control, KiteFileProtectionProtocol.decodeControl(
            KiteFileProtectionProtocol.encodeControl(control)
        ))
        assertNull(KiteFileProtectionProtocol.decodeControl("schema=other\n"))
        assertNull(KiteFileProtectionProtocol.normalizeRelativePath("../outside"))
        assertNull(KiteFileProtectionProtocol.normalizeRelativePath("a/../../outside"))
        assertEquals("user-home", KiteFileProtectionProtocol.normalizeRelativePath("user-home/."))
        assertEquals("user-home/data", KiteFileProtectionProtocol.normalizeRelativePath("./user-home//data"))
    }

    @Test
    fun `entry 合同保存 Unicode 路径符号链接目标与权限`() {
        val entry = KiteFileProtectionEntry(
            relativePath = "用户数据/当前版本",
            beforeKind = KiteFileProtectionBeforeKind.Symlink,
            mode = 0x1FF,
            linkTarget = "../versions/旧版本"
        )

        val encoded = KiteFileProtectionProtocol.encodeEntry(entry)

        assertTrue("capture_state=complete" in encoded)
        assertEquals(entry, KiteFileProtectionProtocol.decodeEntry(encoded))
    }

    @Test
    fun `不完整 entry 不能被恢复器消费`() {
        val incomplete = """
            schema=$KITE_FILE_PROTECTION_ENTRY_SCHEMA
            capture_state=copying
            relative_path_hex=62696e2f746f6f6c
            before_kind=file
            mode=493
            link_target_hex=
        """.trimIndent()

        assertNull(KiteFileProtectionProtocol.decodeEntry(incomplete))
    }

    @Test
    fun `区间 entry 保存原始大小并拒绝非文件类型`() {
        val entry = KiteFileProtectionEntry(
            relativePath = "large.bin",
            beforeKind = KiteFileProtectionBeforeKind.File,
            mode = 0x1A4,
            storageMode = KiteFileProtectionStorageMode.RangeUndo,
            originalSize = 128L * 1024L * 1024L
        )

        assertEquals(entry, KiteFileProtectionProtocol.decodeEntry(KiteFileProtectionProtocol.encodeEntry(entry)))
        val invalid = runCatching {
            KiteFileProtectionProtocol.encodeEntry(
                entry.copy(beforeKind = KiteFileProtectionBeforeKind.Directory)
            )
        }
        assertTrue(invalid.isFailure)
    }
}
