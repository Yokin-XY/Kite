package com.kite.app.platform.resources

import com.kite.app.foundation.fileprotection.KiteFileProtectionBeforeKind
import com.kite.app.foundation.fileprotection.KiteFileProtectionEntry
import com.kite.app.foundation.fileprotection.KiteFileProtectionProtocol
import com.kite.app.foundation.runtime.ProotViewBinding
import com.kite.app.foundation.runtime.ProotViewStore
import com.kite.app.platform.fileprotection.FileProtectionJournalReader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.Properties

@RunWith(RobolectricTestRunner::class)
class ResourceTransactionCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `引导任务使用独立子 View 且提交后不保留恢复点`() {
        val fixture = fixture()
        val initialViewId = fixture.viewStore.ensureInitialized().currentViewId

        fixture.coordinator.beginUpdate(RESOURCE_ID, RUN_ID, "1.0.0", "2.0.0").getOrThrow()
        val environment = fixture.coordinator.environmentForRun(RUN_ID)
        val childViewId = environment.getValue(ProotViewBinding.ENV_VIEW_ID)
        assertNotEquals(initialViewId, childViewId)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.viewStore.currentBinding()
        }
        assertTrue(File(environment.getValue(ProotViewBinding.ENV_CONTROL_PATH)).readText().contains(
            "scope_root=${fixture.managedSoftware.absolutePath}"
        ))

        fixture.coordinator.commitUpdate(RESOURCE_ID, RUN_ID).getOrThrow()
        fixture.coordinator.finalizeUpdate(RESOURCE_ID, RUN_ID).getOrThrow()

        assertEquals(childViewId, fixture.viewStore.currentBinding().viewId)
        assertFalse(File(fixture.storeRoot, "active/default.json").exists())
        assertTrue(File(fixture.storeRoot, "checkpoints").listFiles().isNullOrEmpty())
    }

    @Test
    fun `更新失败只废弃本次子 View 并保持 current 不变`() {
        val fixture = fixture()
        val initialViewId = fixture.viewStore.ensureInitialized().currentViewId

        fixture.coordinator.beginUpdate(RESOURCE_ID, RUN_ID, "1.0.0", "2.0.0").getOrThrow()
        val childViewId = fixture.coordinator.environmentForRun(RUN_ID)
            .getValue(ProotViewBinding.ENV_VIEW_ID)
        val restored = fixture.coordinator.rollbackUpdate(RESOURCE_ID, RUN_ID).getOrThrow()

        assertEquals(initialViewId, fixture.viewStore.currentBinding().viewId)
        assertEquals("1.0.0", restored.restoredVersion)
        assertFalse(fixture.viewStore.recover()!!.views.any { it.viewId == childViewId })
    }

    @Test
    fun `应用中断时未切换的子 View 会自动回收`() {
        val fixture = fixture()
        val initialViewId = fixture.viewStore.ensureInitialized().currentViewId
        fixture.coordinator.beginUpdate(RESOURCE_ID, RUN_ID, "1.0.0", "2.0.0").getOrThrow()

        val restarted = fixture.recreateCoordinator()
        val recovery = restarted.recoverInterruptedTransactions().single()

        assertTrue(recovery.restored)
        assertFalse(recovery.committed)
        assertEquals(initialViewId, fixture.newViewStore("after-restart").currentBinding().viewId)
    }

    @Test
    fun `子 View 已删除但事务记录未落盘时回退仍可幂等收尾`() {
        val fixture = fixture()
        val initialViewId = fixture.viewStore.ensureInitialized().currentViewId
        fixture.coordinator.beginUpdate(RESOURCE_ID, RUN_ID, "1.0.0", "2.0.0").getOrThrow()
        val record = JSONObject(File(fixture.storeRoot, "active/default.json").readText())
        val childViewId = record.getString("viewId")
        val ownerId = record.getString("ownerId")

        fixture.viewStore.releaseLease(childViewId, ownerId)
        fixture.viewStore.discard(childViewId)

        val result = fixture.coordinator.rollbackUpdate(RESOURCE_ID, RUN_ID).getOrThrow()
        assertEquals("1.0.0", result.restoredVersion)
        assertEquals(initialViewId, fixture.viewStore.currentBinding().viewId)
    }

    @Test
    fun `View 已切换但业务记录未收尾时按提交成功恢复`() {
        val fixture = fixture()
        fixture.coordinator.beginUpdate(RESOURCE_ID, RUN_ID, "1.0.0", "2.0.0").getOrThrow()
        val childViewId = fixture.coordinator.environmentForRun(RUN_ID)
            .getValue(ProotViewBinding.ENV_VIEW_ID)
        fixture.coordinator.commitUpdate(RESOURCE_ID, RUN_ID).getOrThrow()

        val restarted = fixture.recreateCoordinator()
        val recovery = restarted.recoverInterruptedTransactions().single()

        assertTrue(recovery.committed)
        assertFalse(recovery.restored)
        assertEquals("2.0.0", recovery.targetVersion)
        assertEquals(childViewId, fixture.newViewStore("after-commit-restart").currentBinding().viewId)
        assertTrue(File(fixture.storeRoot, "checkpoints").listFiles().isNullOrEmpty())
    }

    @Test
    fun `拒绝保护作用域之外的资源更新`() {
        val fixture = fixture()
        val outside = File(fixture.baseRoot, "shared/default/user-data").also(File::mkdirs)
        val coordinator = fixture.createCoordinator { id -> ResourceTransactionTarget(id, outside) }

        assertThrows(IllegalArgumentException::class.java) {
            coordinator.beginUpdate(RESOURCE_ID, RUN_ID, "1", "2").getOrThrow()
        }
    }

    @Test
    fun `升级前已提交的长期恢复点在启动时清理`() {
        val fixture = fixture()
        val tool = File(fixture.installRoot, "tool").also { it.writeText("new") }
        val operationId = "$RESOURCE_ID-legacy"
        val operationRoot = File(File(fixture.storeRoot, RESOURCE_ID), operationId).also(File::mkdirs)
        val entriesRoot = File(operationRoot, "entries")
        val entryRoot = File(entriesRoot, "legacy-tool").also(File::mkdirs)
        File(entryRoot, FileProtectionJournalReader.META_FILE_NAME).writeText(
            KiteFileProtectionProtocol.encodeEntry(
                KiteFileProtectionEntry(
                    relativePath = "tool",
                    beforeKind = KiteFileProtectionBeforeKind.File,
                    mode = 0x1A4
                )
            )
        )
        File(entryRoot, FileProtectionJournalReader.PAYLOAD_NAME).writeText("old")
        val record = Properties().apply {
            setProperty("schema", "kf_resource_txn_record_v1")
            setProperty("transactionId", operationId)
            setProperty("resourceId", RESOURCE_ID)
            setProperty("rootHostPath", fixture.installRoot.absolutePath)
            setProperty("entriesHostPath", entriesRoot.absolutePath)
            setProperty("phase", "Committed")
            setProperty("startedAt", "100")
            setProperty("committedAt", "200")
            setProperty("lastError", "")
            setProperty("previousVersion", "0.9.0")
            setProperty("targetVersion", "1.0.0")
        }
        FileOutputStream(File(operationRoot, "record.properties")).use { output ->
            record.store(output, null)
        }

        val recovered = fixture.recreateCoordinator().recoverInterruptedTransactions()

        assertTrue(recovered.isEmpty())
        assertEquals("new", tool.readText())
        assertFalse(operationRoot.exists())
    }

    private fun fixture(): Fixture {
        val root = temporaryFolder.newFolder()
        val baseRoot = File(root, "runtime").also(File::mkdirs)
        val managedSoftware = File(baseRoot, "shared/default/.kf/software").also(File::mkdirs)
        val installRoot = File(managedSoftware, RESOURCE_ID).also(File::mkdirs)
        val storeRoot = File(root, "transactions")
        val viewRoot = File(root, "views")
        val controlFile = File(root, "legacy/kf-resource-transaction.active")
        var clock = 1_000L
        var sequence = 0
        fun newViewStore(session: String) = ProotViewStore(
            rootDirectory = viewRoot,
            containerId = "ubuntu-main",
            baseRootDirectory = baseRoot,
            scopeRootDirectories = listOf(managedSoftware),
            now = { ++clock },
            idFactory = { "id-${++sequence}" },
            processSessionId = session
        )
        val viewStore = newViewStore("initial-process")
        fun create(
            targetResolver: (String) -> ResourceTransactionTarget? = {
                ResourceTransactionTarget(it, installRoot)
            },
            store: ProotViewStore = viewStore
        ) = ResourceTransactionCoordinator(
            storeRoot = storeRoot,
            controlFile = controlFile,
            targetResolver = targetResolver,
            viewStoreProvider = { store },
            now = { ++clock },
            nativePath = File::getAbsolutePath
        )
        return Fixture(
            baseRoot = baseRoot,
            managedSoftware = managedSoftware,
            installRoot = installRoot,
            storeRoot = storeRoot,
            viewStore = viewStore,
            newViewStore = ::newViewStore,
            createCoordinator = { resolver -> create(resolver) },
            recreateCoordinator = {
                create(store = newViewStore("restart-${++sequence}"))
            },
            coordinator = create()
        )
    }

    private data class Fixture(
        val baseRoot: File,
        val managedSoftware: File,
        val installRoot: File,
        val storeRoot: File,
        val viewStore: ProotViewStore,
        val newViewStore: (String) -> ProotViewStore,
        val createCoordinator: ((String) -> ResourceTransactionTarget?) -> ResourceTransactionCoordinator,
        val recreateCoordinator: () -> ResourceTransactionCoordinator,
        val coordinator: ResourceTransactionCoordinator
    )

    companion object {
        private const val RESOURCE_ID = "kite.test.resource"
        private const val RUN_ID = "resource-run-1"
    }
}
