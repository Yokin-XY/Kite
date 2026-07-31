package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class ProotViewStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun lifecycleBuildsImmutableParentChainAndKeepsCurrentAtomic() {
        val fixture = fixture()
        val initial = fixture.store.ensureInitialized()
        assertEquals(initial, fixture.store.ensureInitialized())
        val baseView = requireNotNull(initial.current)

        val first = fixture.store.prepare("first-update")
        assertEquals(baseView.viewId, first.parentViewId)
        assertTrue(File(first.controlFilePath).readText().contains(
            "parent_upper_root=${baseView.upperRootPath}"
        ))
        assertEquals(ProotViewState.READY, fixture.store.verify(first.viewId).state)
        assertEquals(ProotViewState.READY, fixture.store.verify(first.viewId).state)
        fixture.store.acquireLease(first.viewId, "update-one", ProotViewLeaseMode.WRITER)
        val committed = fixture.store.commit(first.viewId, "update-one")
        assertEquals(ProotViewState.CURRENT, committed.state)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.currentBinding()
        }
        fixture.store.releaseLease(first.viewId, "update-one")
        assertEquals(first.viewId, fixture.store.currentBinding().viewId)
        assertEquals(listOf(baseView.viewId), fixture.store.currentBinding().parentViewIds)

        val second = fixture.store.prepare("second-update")
        val secondControl = File(second.controlFilePath).readText()
        assertTrue(secondControl.indexOf("parent_upper_root=${first.upperRootPath}") <
            secondControl.indexOf("parent_upper_root=${baseView.upperRootPath}"))
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.discard(baseView.viewId)
        }
        assertEquals(first.viewId, fixture.store.currentBinding().viewId)

        assertTrue(fixture.store.releaseLease(first.viewId, "missing-owner").leases.isEmpty())
        assertTrue(File(fixture.root, "current.json").readText().contains(first.viewId))
        assertTrue(File(fixture.root, "wal.jsonl").readLines().any {
            JSONObject(it).optString("operation") == "COMMIT" &&
                JSONObject(it).optString("phase") == "COMMIT"
        })
    }

    @Test
    fun leasesAllowReadersOrOneWriterButNeverBoth() {
        val fixture = fixture()
        val current = requireNotNull(fixture.store.ensureInitialized().current)

        val writer = fixture.store.acquireLease(
            current.viewId,
            "writer-one",
            ProotViewLeaseMode.WRITER
        )
        assertEquals(1, writer.leases.size)
        assertEquals(writer, fixture.store.acquireLease(
            current.viewId,
            "writer-one",
            ProotViewLeaseMode.WRITER
        ))
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.acquireLease(current.viewId, "reader-one", ProotViewLeaseMode.READER)
        }

        fixture.store.releaseLease(current.viewId, "writer-one")
        fixture.store.acquireLease(current.viewId, "reader-one", ProotViewLeaseMode.READER)
        val readers = fixture.store.acquireLease(
            current.viewId,
            "reader-two",
            ProotViewLeaseMode.READER
        )
        assertEquals(2, readers.leases.size)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.acquireLease(current.viewId, "writer-two", ProotViewLeaseMode.WRITER)
        }
    }

    @Test
    fun recoveryDropsPreviousProcessLeasesAndCommitRejectsBranches() {
        val fixture = fixture()
        val initial = requireNotNull(fixture.store.ensureInitialized().current)
        val first = fixture.store.prepare("first-branch")
        fixture.store.verify(first.viewId)
        fixture.store.acquireLease(first.viewId, "old-process", ProotViewLeaseMode.WRITER)

        val restarted = fixture.newStore("new-process")
        val recoveredFirst = requireNotNull(restarted.recover()).views.first {
            it.viewId == first.viewId
        }
        assertTrue(recoveredFirst.leases.isEmpty())

        restarted.acquireLease(first.viewId, "new-process", ProotViewLeaseMode.WRITER)
        restarted.commit(first.viewId, "new-process")
        val branch = restarted.prepare("old-parent-branch", initial.viewId)
        restarted.verify(branch.viewId)
        restarted.acquireLease(branch.viewId, "branch-writer", ProotViewLeaseMode.WRITER)
        assertThrows(IllegalArgumentException::class.java) {
            restarted.commit(branch.viewId, "branch-writer")
        }
    }

    @Test
    fun recoveryUsesDurableCurrentPointerAndFallsBackFromBrokenView() {
        val fixture = fixture()
        val initial = requireNotNull(fixture.store.ensureInitialized().current)
        val child = fixture.store.prepare("recover-update")
        fixture.store.verify(child.viewId)
        fixture.store.acquireLease(child.viewId, "recover-owner", ProotViewLeaseMode.WRITER)
        fixture.store.commit(child.viewId, "recover-owner")

        val catalogFile = File(fixture.root, "catalog.json")
        val stale = JSONObject(catalogFile.readText())
        stale.put("currentViewId", initial.viewId)
        val views = stale.getJSONArray("views")
        for (index in 0 until views.length()) {
            val view = views.getJSONObject(index)
            view.put(
                "state",
                if (view.getString("viewId") == initial.viewId) {
                    ProotViewState.CURRENT.name
                } else {
                    ProotViewState.READY.name
                }
            )
        }
        catalogFile.writeText(stale.toString(2))
        File(fixture.root, ".catalog.json.tmp-crash").writeText("partial")

        val recovered = fixture.newStore("fixture-process").recover()
        assertEquals(child.viewId, recovered?.currentViewId)
        assertFalse(File(fixture.root, ".catalog.json.tmp-crash").exists())

        File(child.controlFilePath).delete()
        val fallback = fixture.newStore("fixture-process").recover()
        assertEquals(initial.viewId, fallback?.currentViewId)
        assertEquals(ProotViewState.BROKEN, fallback?.views?.first {
            it.viewId == child.viewId
        }?.state)
    }

    @Test
    fun ordinaryQueriesReuseRecoveredCatalogAndExplicitRecoveryCleansTemporaryFiles() {
        val fixture = fixture()
        val initialized = fixture.store.ensureInitialized()
        val temporary = File(fixture.root, ".catalog.json.tmp-late").apply {
            writeText("partial")
        }

        assertEquals(initialized.environmentCurrents, fixture.store.environmentCurrents())
        assertTrue(temporary.exists())

        fixture.store.recover()

        assertFalse(temporary.exists())
    }

    @Test
    fun ordinaryLaunchNeverActivatesViewAndExplicitRequestsRemainFailClosed() {
        val filesRoot = temporaryFolder.newFolder("files")
        val runtimeRoot = File(filesRoot, "runtime").apply { mkdirs() }
        val containerRoot = File(runtimeRoot, "containers/ubuntu-main").apply { mkdirs() }
        val rootfs = File(containerRoot, "rootfs").apply { mkdirs() }
        val workspace = File(runtimeRoot, "shared/default").apply { mkdirs() }
        val container = ContainerRecord(
            id = "ubuntu-main",
            displayName = "Ubuntu",
            imageName = "ubuntu-base",
            rootfsPath = rootfs.absolutePath,
            workspacePath = workspace.absolutePath,
            createdAt = 1L
        )
        val store = ProotViewStore.forContainer(container)
        assertSame(store, ProotViewStore.forContainer(container))
        store.ensureInitialized()
        assertFalse(store.isEnabled())

        // runtime 不具备完整 View 能力时返回 null（兼容边界）。
        assertNull(ProotViewRuntime.resolveActiveBinding(container, JSONObject()
            .put("capabilities", JSONArray())))
        assertNull(ProotViewRuntime.resolveActiveBinding(
            container,
            JSONObject().put("capabilities", JSONArray()
                .put(ProotViewStore.RUNTIME_CAPABILITY))
        ))
        assertThrows(IllegalArgumentException::class.java) {
            ProotViewRuntime.resolveActiveBinding(
                container = container,
                runtimeDescriptor = JSONObject().put("capabilities", JSONArray()),
                requestedViewId = "explicit-view"
            )
        }
        val ordinary = ProotViewRuntime.resolveActiveBinding(
            container,
            JSONObject().put("capabilities", JSONArray()
                .put(ProotViewStore.RUNTIME_CAPABILITY)
                .put(ProotViewStore.BLOCK_RUNTIME_CAPABILITY))
        )
        assertNull(ordinary)
        assertFalse(store.isEnabled())

        // 升级前遗留的 activation 不能把普通启动重新带回全局 View。
        store.enable()
        val legacyActivated = ProotViewRuntime.resolveActiveBinding(
            container,
            JSONObject().put("capabilities", JSONArray()
                .put(ProotViewStore.RUNTIME_CAPABILITY)
                .put(ProotViewStore.BLOCK_RUNTIME_CAPABILITY))
        )
        assertNull(legacyActivated)
        assertTrue(store.isEnabled())
    }

    @Test
    fun explicitBindingLaunchesPreparedWriterViewWhileCurrentIsLocked() {
        val filesRoot = temporaryFolder.newFolder("explicit-view-files")
        val runtimeRoot = File(filesRoot, "runtime").apply { mkdirs() }
        val container = ContainerRecord(
            id = "ubuntu-main",
            displayName = "Ubuntu",
            imageName = "ubuntu-base",
            rootfsPath = File(runtimeRoot, "containers/ubuntu-main/rootfs").apply { mkdirs() }.absolutePath,
            workspacePath = File(runtimeRoot, "shared/default").apply { mkdirs() }.absolutePath,
            createdAt = 1L
        )
        val store = ProotViewStore.forContainer(container)
        val current = requireNotNull(store.ensureInitialized().current)
        store.enable()
        store.acquireLease(current.viewId, "resource-update", ProotViewLeaseMode.WRITER)
        val prepared = store.prepare("resource-update")
        store.verify(prepared.viewId)
        store.acquireLease(prepared.viewId, "resource-update", ProotViewLeaseMode.WRITER)
        val descriptor = JSONObject().put("capabilities", JSONArray()
            .put(ProotViewStore.RUNTIME_CAPABILITY)
            .put(ProotViewStore.BLOCK_RUNTIME_CAPABILITY))

        // 普通 PRoot 不再解析 View，因此也不会被事务 writer lease 阻塞。
        assertNull(ProotViewRuntime.resolveActiveBinding(container, descriptor))
        val explicit = ProotViewRuntime.resolveActiveBinding(
            container = container,
            runtimeDescriptor = descriptor,
            requestedViewId = prepared.viewId
        )

        assertEquals(prepared.viewId, explicit?.viewId)
        assertEquals(listOf(current.viewId), explicit?.parentViewIds)
    }

    @Test
    fun commitAcceptsChecksummedSparseBlockArtifactsAndReportsStorage() {
        val fixture = fixture()
        fixture.store.ensureInitialized()
        val baseFile = File(fixture.base, "payload.bin").also {
            it.writeBytes(ByteArray(BLOCK_SIZE_BYTES) { index -> (index % 251).toByte() })
        }
        val child = fixture.store.prepare("block-update")
        writeBlockArtifact(child, "payload.bin", baseFile)
        fixture.store.verify(child.viewId)
        fixture.store.acquireLease(child.viewId, "block-writer", ProotViewLeaseMode.WRITER)

        val stats = fixture.store.storageStats(child.viewId)
        assertEquals(1, stats.blockFileCount)
        assertEquals(BLOCK_SIZE_BYTES.toLong(), stats.blockDeltaLogicalBytes)
        assertEquals((BLOCK_META_SLOT_BYTES * 2L) + baseFile.absolutePath.length + 1L,
            stats.blockMetadataBytes)
        assertEquals(0, stats.temporaryEntryCount)

        assertEquals(child.viewId, fixture.store.commit(child.viewId, "block-writer").viewId)
    }

    @Test
    fun commitRejectsCorruptBlockMetadataWithoutSwitchingCurrent() {
        val fixture = fixture()
        val initial = requireNotNull(fixture.store.ensureInitialized().current)
        val baseFile = File(fixture.base, "payload.bin").also {
            it.writeBytes(ByteArray(BLOCK_SIZE_BYTES) { 7 })
        }
        val child = fixture.store.prepare("corrupt-block-update")
        val meta = writeBlockArtifact(child, "payload.bin", baseFile)
        fixture.store.verify(child.viewId)
        fixture.store.acquireLease(child.viewId, "block-writer", ProotViewLeaseMode.WRITER)
        RandomAccessFile(meta, "rw").use { file ->
            file.seek(128L)
            file.write(0x7f)
            file.seek(BLOCK_META_SLOT_BYTES + 128L)
            file.write(0x7f)
        }

        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.commit(child.viewId, "block-writer")
        }
        fixture.store.releaseLease(child.viewId, "block-writer")
        assertEquals(initial.viewId, fixture.store.currentBinding().viewId)
    }

    @Test
    fun initializationRemovesOnlyOrphanDirectoriesInsideOwnedViewRoot() {
        val fixture = fixture()
        val orphan = File(fixture.root, "views/orphan-view").apply { mkdirs() }
        File(orphan, "partial.meta").writeText("partial")

        fixture.store.ensureInitialized()

        assertFalse(orphan.exists())
        assertTrue(File(fixture.root, "views").isDirectory)
    }

    @Test
    fun scopedControlOnlyPublishesProtectedSubtrees() {
        val base = temporaryFolder.newFolder("scoped-base")
        val software = File(base, "shared/default/.kf/software").apply { mkdirs() }
        val bin = File(base, "shared/default/.kf/bin").apply { mkdirs() }
        File(base, "shared/default/user.txt").writeText("persistent")
        val store = ProotViewStore(
            rootDirectory = temporaryFolder.newFolder("scoped-store"),
            containerId = "ubuntu-main",
            baseRootDirectory = base,
            scopeRootDirectories = listOf(software, bin),
            now = { 1_000L },
            idFactory = { "scoped" },
            processSessionId = "scoped-process"
        )

        val binding = store.ensureInitialized().current!!.let { store.binding(it.viewId) }
        val control = File(binding.controlFilePath).readText()

        assertEquals(listOf(software.absolutePath, bin.absolutePath), binding.scopeRootPaths)
        assertTrue(control.contains("scope_root=${software.absolutePath}"))
        assertTrue(control.contains("scope_root=${bin.absolutePath}"))
        assertFalse(control.contains("scope_root=${base.absolutePath}\n"))
    }

    @Test
    fun pathProjectionReadsParentLayerAndAlwaysWritesCurrentUpper() {
        val fixture = fixture()
        val initial = requireNotNull(fixture.store.ensureInitialized().current)
        val baseFile = File(fixture.base, "containers/ubuntu-main/rootfs/root/.config/opencode/opencode.jsonc")
        baseFile.parentFile?.mkdirs()
        baseFile.writeText("base")
        val relative = baseFile.relativeTo(fixture.base).path
        val parentFile = File(initial.upperRootPath, relative).apply {
            parentFile?.mkdirs()
            writeText("parent")
        }
        val child = fixture.store.prepare("configuration-write")
        fixture.store.verify(child.viewId)
        fixture.store.acquireLease(child.viewId, "configuration-owner", ProotViewLeaseMode.WRITER)
        fixture.store.commit(child.viewId, "configuration-owner")
        fixture.store.releaseLease(child.viewId, "configuration-owner")

        val projection = fixture.store.projectPath(baseFile)

        assertEquals(parentFile.canonicalFile, projection.visibleFile?.canonicalFile)
        assertEquals(
            File(child.upperRootPath, relative).canonicalFile,
            projection.writableFile.canonicalFile
        )
        assertFalse(projection.writableFile.exists())
        assertEquals(
            listOf(child.upperRootPath, initial.upperRootPath, fixture.base.canonicalPath),
            projection.layerRootPaths
        )
    }

    @Test
    fun pathProjectionRespectsNearestWhiteoutIncludingAncestors() {
        val fixture = fixture()
        val current = requireNotNull(fixture.store.ensureInitialized().current)
        val baseFile = File(fixture.base, "containers/ubuntu-main/rootfs/root/.config/opencode/opencode.jsonc")
        baseFile.parentFile?.mkdirs()
        baseFile.writeText("base")
        val relativeDirectory = requireNotNull(baseFile.parentFile).relativeTo(fixture.base).path
        File(current.whiteoutRootPath, relativeDirectory).apply {
            parentFile?.mkdirs()
            writeText("")
        }

        val projection = fixture.store.projectPath(baseFile)

        assertNull(projection.visibleFile)
        assertEquals(
            File(current.upperRootPath, baseFile.relativeTo(fixture.base).path).canonicalFile,
            projection.writableFile.canonicalFile
        )
    }

    private fun fixture(): StoreFixture {
        val base = temporaryFolder.newFolder("base-${System.nanoTime()}")
        val root = temporaryFolder.newFolder("store-${System.nanoTime()}")
        var clock = 10_000L
        var sequence = 0
        fun createStore(processSessionId: String = "fixture-process") = ProotViewStore(
            rootDirectory = root,
            containerId = "ubuntu-main",
            baseRootDirectory = base,
            now = { ++clock },
            idFactory = { "id-${++sequence}" },
            processSessionId = processSessionId
        )
        return StoreFixture(base, root, createStore(), ::createStore)
    }

    private fun writeBlockArtifact(
        record: ProotViewRecord,
        relativePath: String,
        source: File
    ): File {
        val upper = File(record.upperRootPath)
        val delta = File(upper, relativePath).apply {
            parentFile?.mkdirs()
            RandomAccessFile(this, "rw").use { file ->
                file.setLength(BLOCK_SIZE_BYTES.toLong())
                file.seek(0L)
                file.write(ByteArray(4096) { 0x5a })
            }
        }
        val blockRoot = File(upper, ".kite-proot-view/blocks")
        val meta = File(blockRoot, "$relativePath.meta").apply { parentFile?.mkdirs() }
        File(blockRoot, "$relativePath.source").apply {
            parentFile?.mkdirs()
            writeText(source.absolutePath + "\n")
        }
        val first = blockMetaSlot(source.length(), delta.length(), generation = 1L)
        val second = blockMetaSlot(source.length(), delta.length(), generation = 2L)
        meta.writeBytes(first + second)
        return meta
    }

    private fun blockMetaSlot(baseSize: Long, visibleSize: Long, generation: Long): ByteArray {
        val bytes = ByteArray(BLOCK_META_SLOT_BYTES)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(BLOCK_META_MAGIC)
        buffer.putInt(1)
        buffer.putInt(BLOCK_SIZE_BYTES)
        buffer.putLong(generation)
        buffer.putLong(baseSize)
        buffer.putLong(visibleSize)
        buffer.putLong(baseSize)
        buffer.putLong((BLOCK_META_SLOT_BYTES - 64).toLong())
        buffer.putLong(0L)
        bytes[64] = 1
        buffer.putLong(56, checksum(bytes))
        return bytes
    }

    private fun checksum(bytes: ByteArray): Long {
        var hash = 1469598103934665603L
        bytes.forEach { byte ->
            hash = hash xor (byte.toLong() and 0xffL)
            hash *= 1099511628211L
        }
        return hash
    }

    private data class StoreFixture(
        val base: File,
        val root: File,
        val store: ProotViewStore,
        val newStore: (String) -> ProotViewStore
    )

    companion object {
        private const val BLOCK_SIZE_BYTES = 64 * 1024
        private const val BLOCK_META_SLOT_BYTES = 4096
        private const val BLOCK_META_MAGIC = 0x4b46424c4f434b32L
    }
}
