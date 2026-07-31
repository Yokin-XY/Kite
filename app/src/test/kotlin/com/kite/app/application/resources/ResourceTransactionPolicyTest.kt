package com.kite.app.application.fileprotection

import com.kite.app.foundation.fileprotection.KiteFileProtectionBeforeKind
import com.kite.app.foundation.fileprotection.KiteFileProtectionEntry
import com.kite.app.platform.fileprotection.FileProtectionRollbackOperation
import com.kite.app.platform.fileprotection.FileProtectionRollbackPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileProtectionPolicyTest {
    @Test
    fun `准备中断和失败恢复点都能进入回滚`() {
        assertEquals(
            FileProtectionPhase.RollingBack,
            FileProtectionStateMachine.transition(
                FileProtectionPhase.Preparing,
                FileProtectionEvent.OperationFailed
            )
        )
        assertEquals(
            FileProtectionPhase.RollingBack,
            FileProtectionStateMachine.transition(
                FileProtectionPhase.Failed,
                FileProtectionEvent.RestoreRequested
            )
        )
    }

    @Test
    fun `同一路径持续写入只保存一次原始事实`() {
        val index = FileProtectionJournalIndex()
        val entry = entry("bin/opencode", KiteFileProtectionBeforeKind.File)

        assertTrue(index.claim(entry.relativePath))
        assertFalse(index.claim(entry.copy(mode = 0x180).relativePath))
        assertEquals(1, index.size())
    }

    @Test
    fun `失败自动进入回滚而成功保留恢复点`() {
        val active = FileProtectionStateMachine.transition(
            FileProtectionPhase.Preparing,
            FileProtectionEvent.Activate
        )
        assertEquals(
            FileProtectionPhase.RollingBack,
            FileProtectionStateMachine.transition(active, FileProtectionEvent.OperationFailed)
        )
        assertEquals(
            FileProtectionPhase.Committed,
            FileProtectionStateMachine.transition(active, FileProtectionEvent.OperationSucceeded)
        )
        assertEquals(
            FileProtectionPhase.RollingBack,
            FileProtectionStateMachine.transition(
                FileProtectionPhase.Committed,
                FileProtectionEvent.RestoreRequested
            )
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `未提交事务不能伪装成可修复恢复点`() {
        FileProtectionStateMachine.transition(
            FileProtectionPhase.Active,
            FileProtectionEvent.RestoreRequested
        )
    }

    @Test
    fun `回滚先删除深层当前对象再按层级恢复全部原始类型`() {
        val entries = listOf(
            entry("bin", KiteFileProtectionBeforeKind.Directory, mode = 0x1ED),
            entry("bin/opencode", KiteFileProtectionBeforeKind.File, mode = 0x1ED),
            entry("current", KiteFileProtectionBeforeKind.Symlink, mode = 0x1FF, linkTarget = "bin/opencode"),
            entry("generated/cache", KiteFileProtectionBeforeKind.Absent)
        )

        val operations = FileProtectionRollbackPlanner.plan(entries)
        val deletes = operations.filterIsInstance<FileProtectionRollbackOperation.DeleteCurrent>()
        val restores = operations.filterIsInstance<FileProtectionRollbackOperation.RestoreBefore>()

        assertEquals(listOf("generated/cache", "bin/opencode", "current", "bin"), deletes.map { it.relativePath })
        assertEquals(listOf("bin", "current", "bin/opencode"), restores.map { it.relativePath })
        assertEquals(0x1ED, restores.first { it.relativePath == "bin/opencode" }.entry.mode)
        assertEquals("bin/opencode", restores.first { it.relativePath == "current" }.entry.linkTarget)
    }

    @Test
    fun `第二次成功更新后只淘汰旧恢复点`() {
        val checkpoints = listOf(
            checkpoint("old", 100),
            checkpoint("latest", 200),
            checkpoint("failed", 300, FileProtectionPhase.Failed)
        )

        assertEquals(
            listOf("old"),
            FileProtectionRetentionPolicy.obsoleteCheckpoints(checkpoints).map { it.operationId }
        )
    }

    private fun entry(
        path: String,
        kind: KiteFileProtectionBeforeKind,
        mode: Int = 0,
        linkTarget: String = ""
    ) = KiteFileProtectionEntry(path, kind, mode, linkTarget)

    private fun checkpoint(
        id: String,
        committedAt: Long,
        phase: FileProtectionPhase = FileProtectionPhase.Committed
    ) = FileProtectionCheckpoint(id, "kite.example", phase, committedAt)
}
