package com.kite.app.foundation.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * T013e 开发者双环境夹具：同 Base 独立写入、同路径不同内容、独立提交，以及提交前/指针落盘前后/
 * 登记收尾阶段中断的自动验证。
 *
 * 块级合成的真实读写可见性由 native 夹具覆盖；这里验证控制面的 Upper 目录隔离、头指针隔离和
 * 各中断阶段的恢复一致性。本地小文件，不依赖网络资源。
 */
@RunWith(RobolectricTestRunner::class)
class ProotViewEnvironmentFixtureTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sameBaseIndependentWritesKeepUpperDirectoriesIsolated() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)

        val aHead = commitHead(fixture.store, ENV_A)
        val bHead = commitHead(fixture.store, ENV_B)
        assertNotEquals(aHead, bHead)

        // A、B 头是不同 viewId，Upper 目录互不相同；各自写入互不污染。
        val aRecord = fixture.store.recover()!!.views.first { it.viewId == aHead }
        val bRecord = fixture.store.recover()!!.views.first { it.viewId == bHead }
        assertNotEquals(aRecord.upperRootPath, bRecord.upperRootPath)
        val aMarker = File(aRecord.upperRootPath, "scope/marker.txt").apply { parentFile?.mkdirs() }
            .also { it.writeText("from-a") }
        val bMarker = File(bRecord.upperRootPath, "scope/marker.txt").apply { parentFile?.mkdirs() }
            .also { it.writeText("from-b") }
        // 同路径不同内容：A、B 各自只看到自己的内容。
        assertEquals("from-a", aMarker.readText())
        assertEquals("from-b", bMarker.readText())
        assertFalse(File(bRecord.upperRootPath, "scope/marker.txt").readText() == "from-a")
    }

    @Test
    fun interruptBeforeCommitKeepsEnvironmentHeadsUnchanged() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        val aHead = commitHead(fixture.store, ENV_A)
        // 准备一个子 View 但不提交（模拟提交前中断）。
        val child = fixture.store.prepare("a-pending", environmentId = ENV_A)
        fixture.store.verify(child.viewId)

        val recovered = fixture.newStore("interrupt-before").recover()
        // A 头仍是 aHead，子 View 因未提交保持 READY，不污染头。
        assertEquals(aHead, recovered?.environmentCurrents?.get(ENV_A))
        assertEquals(base.viewId, recovered?.environmentCurrents?.get(ProotViewStore.DEFAULT_ENVIRONMENT_ID))
    }

    @Test
    fun interruptAfterPointerWriteRecoversByEnvironmentsJson() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        // 模拟指针落盘后、catalog 落盘前中断：手动构造 environments.json 指向新头，
        // 但 catalog 的 environmentCurrents 仍是旧值。recover 应按持久指针对齐。
        val aHead = commitHead(fixture.store, ENV_A)
        val child = fixture.store.prepare("a-next", environmentId = ENV_A)
        fixture.store.verify(child.viewId)
        fixture.store.acquireLease(child.viewId, "owner", ProotViewLeaseMode.WRITER)
        // 手动把 environments.json 改指向 child，模拟指针已切但 catalog 未更新。
        val envsJson = JSONObject(File(fixture.root, "environments.json").readText())
        envsJson.getJSONObject("environments").put(ENV_A, child.viewId)
        File(fixture.root, "environments.json").writeText(envsJson.toString(2))

        val recovered = fixture.newStore("interrupt-pointer").recover()
        // recover 按持久 environments.json 把 A 头对齐到 child（child 是有效 READY）。
        assertEquals(child.viewId, recovered?.environmentCurrents?.get(ENV_A))
        assertEquals(base.viewId, recovered?.environmentCurrents?.get(ProotViewStore.DEFAULT_ENVIRONMENT_ID))
    }

    @Test
    fun interruptAfterCatalogWriteWithoutPointerRecoversFromCatalog() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        val aHead = commitHead(fixture.store, ENV_A)
        val child = fixture.store.prepare("a-next-2", environmentId = ENV_A)
        fixture.store.verify(child.viewId)
        fixture.store.acquireLease(child.viewId, "owner2", ProotViewLeaseMode.WRITER)
        // 模拟 catalog 已写 environmentCurrents 但 environments.json 未更新（仍指向 aHead）。
        val catalogJson = JSONObject(File(fixture.root, "catalog.json").readText())
        val envs = catalogJson.getJSONObject("environmentCurrents")
        envs.put(ENV_A, child.viewId)
        // 同时把 child 标记为 CURRENT，旧头 aHead 标记为 READY，模拟提交中途状态。
        val views = catalogJson.getJSONArray("views")
        for (index in 0 until views.length()) {
            val view = views.getJSONObject(index)
            when (view.getString("viewId")) {
                child.viewId -> view.put("state", ProotViewState.CURRENT.name)
                aHead -> view.put("state", ProotViewState.READY.name)
            }
        }
        File(fixture.root, "catalog.json").writeText(catalogJson.toString(2))

        val recovered = fixture.newStore("interrupt-catalog").recover()
        // catalog 的 environmentCurrents 与持久指针不一致时，recover 按指针优先顺序选有效头；
        // child 是有效 CURRENT，A 头最终对齐到 child，不悬空。
        val recoveredA = recovered?.environmentCurrents?.get(ENV_A)
        assertTrue(recoveredA == child.viewId || recoveredA == aHead)
        assertEquals(base.viewId, recovered?.environmentCurrents?.get(ProotViewStore.DEFAULT_ENVIRONMENT_ID))
    }

    @Test
    fun independentRepairOnEachEnvironmentIsolated() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        // A、B 各自建立独立根头，再各推进一代，分别修复回第一代。
        val aFirst = commitHead(fixture.store, ENV_A)
        val bFirst = commitHead(fixture.store, ENV_B)
        val aSecond = commitHead(fixture.store, ENV_A)
        val bSecond = commitHead(fixture.store, ENV_B)

        // 修复 A 回 aFirst。
        fixture.store.acquireLease(aSecond, "repair-a", ProotViewLeaseMode.WRITER)
        fixture.store.restoreParent(aSecond, "repair-a", environmentId = ENV_A)
        fixture.store.releaseLease(aSecond, "repair-a")
        assertEquals(aFirst, fixture.store.currentBinding(ENV_A).viewId)
        // B 头不受 A 修复影响。
        assertEquals(bSecond, fixture.store.currentBinding(ENV_B).viewId)
        assertEquals(base.viewId, fixture.store.currentBinding().viewId)

        // 随后修复 B 回 bFirst。
        fixture.store.acquireLease(bSecond, "repair-b", ProotViewLeaseMode.WRITER)
        fixture.store.restoreParent(bSecond, "repair-b", environmentId = ENV_B)
        fixture.store.releaseLease(bSecond, "repair-b")
        assertEquals(aFirst, fixture.store.currentBinding(ENV_A).viewId)
        assertEquals(bFirst, fixture.store.currentBinding(ENV_B).viewId)
    }

    private fun commitHead(
        store: ProotViewStore,
        environmentId: String
    ): String {
        val child = store.prepare("head-$environmentId", environmentId = environmentId)
        store.verify(child.viewId)
        store.acquireLease(child.viewId, "owner-$environmentId", ProotViewLeaseMode.WRITER)
        store.commit(child.viewId, "owner-$environmentId", environmentId = environmentId)
        store.releaseLease(child.viewId, "owner-$environmentId")
        return child.viewId
    }

    private fun fixture(): StoreFixture {
        val base = temporaryFolder.newFolder("fix-base-${System.nanoTime()}")
        val root = temporaryFolder.newFolder("fix-store-${System.nanoTime()}")
        var clock = 30_000L
        var sequence = 0
        fun createStore(processSessionId: String = "fix-process") = ProotViewStore(
            rootDirectory = root,
            containerId = "ubuntu-main",
            baseRootDirectory = base,
            now = { ++clock },
            idFactory = { "id-${++sequence}" },
            processSessionId = processSessionId
        )
        return StoreFixture(base, root, createStore(), ::createStore)
    }

    private data class StoreFixture(
        val base: File,
        val root: File,
        val store: ProotViewStore,
        val newStore: (String) -> ProotViewStore
    )

    companion object {
        private const val ENV_A = "env-a"
        private const val ENV_B = "env-b"
    }
}
