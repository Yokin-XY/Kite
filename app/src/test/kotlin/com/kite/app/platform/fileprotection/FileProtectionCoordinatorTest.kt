package com.kite.app.platform.fileprotection

import com.kite.app.application.fileprotection.FileProtectionBackendId
import com.kite.app.application.fileprotection.ProtectedOperationRequest
import com.kite.app.application.fileprotection.ProtectionScope
import com.kite.app.foundation.fileprotection.KiteFileProtectionBeforeKind
import com.kite.app.foundation.fileprotection.KiteFileProtectionEntry
import com.kite.app.foundation.fileprotection.KiteFileProtectionProtocol
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileProtectionCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `任意 owner 和操作类型可共享同一保护引擎`() {
        val fixture = fixture()
        val target = File(fixture.scopeRoot, "config/state.txt").also {
            it.parentFile?.mkdirs()
            it.writeText("stable")
        }

        fixture.coordinator.begin(
            request(fixture.scopeRoot, ownerId = "experiment.alpha", operationKind = "dangerous_config")
        ).getOrThrow()
        fixture.captureFile("config/state.txt", target)
        target.writeText("changed")
        fixture.coordinator.commit("experiment.alpha").getOrThrow()

        assertTrue(fixture.coordinator.hasCheckpoint("experiment.alpha"))
        val restored = fixture.coordinator.restoreLatest("experiment.alpha").getOrThrow()
        assertEquals("stable", target.readText())
        assertEquals("checkpoint-a", restored.metadata["label"])
        assertFalse(fixture.coordinator.hasCheckpoint("experiment.alpha"))
    }

    @Test
    fun `未注册后端会在发布控制文件前失败`() {
        val fixture = fixture(backends = listOf(WholeObjectPreimageBackend()))

        val result = fixture.coordinator.begin(
            request(
                scopeRoot = fixture.scopeRoot,
                ownerId = "experiment.beta",
                operationKind = "workspace_snapshot",
                backendId = FileProtectionBackendId.RangeUndo
            )
        )

        assertTrue(result.isFailure)
        assertFalse(fixture.controlFile.exists())
        assertTrue(fixture.storeRoot.walkTopDown().none { it.name == "record.properties" })
    }

    @Test
    fun `中断恢复读取持久记录而不是依赖调用方内存`() {
        val fixture = fixture()
        val target = File(fixture.scopeRoot, "payload").also { it.writeText("before") }
        fixture.coordinator.begin(request(fixture.scopeRoot)).getOrThrow()
        fixture.captureFile("payload", target)
        target.writeText("partial")
        fixture.coordinator.deactivateInterruptedOperation()

        val restarted = fixture.recreateCoordinator()
        val recovery = restarted.recoverInterruptedOperations().single()

        assertTrue(recovery.restored)
        assertEquals("before", target.readText())
    }

    @Test
    fun `进程未收敛时恢复门阻止覆盖当前文件`() {
        var guardCalls = 0
        val fixture = fixture(
            restoreGuard = FileProtectionRestoreGuard {
                guardCalls++
                Result.failure(IllegalStateException("process still running"))
            }
        )
        val target = File(fixture.scopeRoot, "payload").also { it.writeText("before") }
        fixture.coordinator.begin(request(fixture.scopeRoot)).getOrThrow()
        fixture.captureFile("payload", target)
        target.writeText("still-mutating")

        val rollback = fixture.coordinator.rollback("experiment.alpha")

        assertTrue(rollback.isFailure)
        assertEquals(1, guardCalls)
        assertEquals("still-mutating", target.readText())
    }

    @Test
    fun `一次性收口清理已提交记录而不回退成功结果`() {
        val fixture = fixture()
        val target = File(fixture.scopeRoot, "payload").also { it.writeText("before") }
        fixture.coordinator.begin(request(fixture.scopeRoot)).getOrThrow()
        fixture.captureFile("payload", target)
        target.writeText("committed")
        fixture.coordinator.commit("experiment.alpha").getOrThrow()

        assertTrue(fixture.coordinator.settleTransientOperations().isEmpty())
        assertEquals("committed", target.readText())
        assertFalse(fixture.coordinator.hasCheckpoint("experiment.alpha"))
    }

    @Test
    fun `一次性收口自动重试上次失败的回滚`() {
        var allowRestore = false
        val fixture = fixture(
            restoreGuard = FileProtectionRestoreGuard {
                if (allowRestore) Result.success(Unit)
                else Result.failure(IllegalStateException("process still running"))
            }
        )
        val target = File(fixture.scopeRoot, "payload").also { it.writeText("before") }
        fixture.coordinator.begin(request(fixture.scopeRoot)).getOrThrow()
        fixture.captureFile("payload", target)
        target.writeText("partial")
        assertTrue(fixture.coordinator.rollback("experiment.alpha").isFailure)

        allowRestore = true
        val recovery = fixture.recreateCoordinator().settleTransientOperations().single()

        assertTrue(recovery.restored)
        assertEquals("before", target.readText())
    }

    private fun fixture(
        backends: Collection<FileProtectionStorageBackend> = listOf(
            WholeObjectPreimageBackend(),
            RangeUndoBackend()
        ),
        restoreGuard: FileProtectionRestoreGuard = FileProtectionRestoreGuard { Result.success(Unit) }
    ): Fixture {
        val root = temporaryFolder.newFolder()
        val scopeRoot = File(root, "scope").also(File::mkdirs)
        val storeRoot = File(root, "protection")
        val controlFile = File(root, "runtime/file-protection.active")
        fun create() = FileProtectionCoordinator(
            storeRoot = storeRoot,
            controlFile = controlFile,
            backends = backends,
            restoreGuard = restoreGuard,
            now = { 7_000L + storeRoot.walkTopDown().count() },
            nativePath = { file -> "/android-test/${file.name}" }
        )
        return Fixture(scopeRoot, storeRoot, controlFile, ::create, create())
    }

    private fun request(
        scopeRoot: File,
        ownerId: String = "experiment.alpha",
        operationKind: String = "dangerous_config",
        backendId: FileProtectionBackendId = FileProtectionBackendId.WholeObjectPreimage
    ) = ProtectedOperationRequest(
        ownerId = ownerId,
        operationKind = operationKind,
        scope = ProtectionScope(scopeRoot.absolutePath),
        metadata = mapOf("label" to "checkpoint-a"),
        preferredBackend = backendId
    )

    private data class Fixture(
        val scopeRoot: File,
        val storeRoot: File,
        val controlFile: File,
        val createCoordinator: () -> FileProtectionCoordinator,
        val coordinator: FileProtectionCoordinator
    ) {
        fun recreateCoordinator(): FileProtectionCoordinator = createCoordinator()

        fun captureFile(relativePath: String, source: File) {
            val control = KiteFileProtectionProtocol.decodeControl(controlFile.readText())
                ?: error("missing test control")
            val journalRoot = File(File(File(storeRoot, "experiment.alpha"), control.operationId), "entries")
            val entryRoot = File(journalRoot, "fixture-${relativePath.hashCode()}").also(File::mkdirs)
            File(entryRoot, FileProtectionJournalReader.META_FILE_NAME).writeText(
                KiteFileProtectionProtocol.encodeEntry(
                    KiteFileProtectionEntry(relativePath, KiteFileProtectionBeforeKind.File, 0x1A4)
                )
            )
            source.copyTo(File(entryRoot, FileProtectionJournalReader.PAYLOAD_NAME), overwrite = true)
        }
    }
}
