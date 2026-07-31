package com.kite.app.foundation.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * T013b 多环境控制层验收：同一不可变 Base 下多个环境头独立共存，旧单 current 无损迁移。
 *
 * 这里只验证控制面身份与头指针隔离；块级文件语义由 ProotViewStoreTest 和 native 夹具覆盖。
 */
@RunWith(RobolectricTestRunner::class)
class ProotViewStoreEnvironmentTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultEnvironmentCoexistsWithIndependentEnvironmentHeads() {
        val fixture = fixture()
        val initial = requireNotNull(fixture.store.ensureInitialized().current)

        // A、B 各自建立直接覆盖同一 Base 的独立根 View，不借用 default 的可写 Upper。
        val aChild = fixture.store.prepare("env-a-update", environmentId = ENV_A)
        assertNull(aChild.parentViewId)
        fixture.store.verify(aChild.viewId)
        fixture.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        fixture.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        fixture.store.releaseLease(aChild.viewId, "owner-a")

        val bChild = fixture.store.prepare("env-b-update", environmentId = ENV_B)
        assertNull(bChild.parentViewId)
        fixture.store.verify(bChild.viewId)
        fixture.store.acquireLease(bChild.viewId, "owner-b", ProotViewLeaseMode.WRITER)
        fixture.store.commit(bChild.viewId, "owner-b", environmentId = ENV_B)
        fixture.store.releaseLease(bChild.viewId, "owner-b")

        val currents = fixture.store.environmentCurrents()
        assertEquals(initial.viewId, currents[ProotViewStore.DEFAULT_ENVIRONMENT_ID])
        assertEquals(aChild.viewId, currents[ENV_A])
        assertEquals(bChild.viewId, currents[ENV_B])
        // 三个头互不相同。
        val heads = listOf(initial.viewId, aChild.viewId, bChild.viewId).toSet()
        assertEquals(3, heads.size)

        // 每个环境绑定到自己的头，互不漂移。
        assertEquals(initial.viewId, fixture.store.currentBinding().viewId)
        assertEquals(aChild.viewId, fixture.store.currentBinding(ENV_A).viewId)
        assertEquals(bChild.viewId, fixture.store.currentBinding(ENV_B).viewId)
        assertTrue(fixture.store.currentBinding(ENV_A).parentViewIds.isEmpty())
        assertTrue(fixture.store.currentBinding(ENV_B).parentViewIds.isEmpty())
        assertNotEquals(initial.upperRootPath, aChild.upperRootPath)
        assertNotEquals(initial.upperRootPath, bChild.upperRootPath)
    }

    @Test
    fun activeEnvironmentDefaultsToDefaultAndPersistsAcrossRestart() {
        val fixture = fixture()
        fixture.store.ensureInitialized()
        assertEquals(ProotViewStore.DEFAULT_ENVIRONMENT_ID, fixture.store.activeEnvironmentId())

        val aRoot = commitEnvironmentHead(fixture.store, ENV_A)
        val switched = fixture.store.switchActiveEnvironment(ENV_A)
        assertEquals(ENV_A, switched.environmentId)
        assertEquals(aRoot, switched.viewId)
        assertEquals(ENV_A, fixture.newStore("active-restart").activeEnvironmentId())
    }

    @Test
    fun unknownEnvironmentCannotBecomeActive() {
        val fixture = fixture()
        fixture.store.ensureInitialized()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.switchActiveEnvironment("missing-environment")
        }
        assertEquals(ProotViewStore.DEFAULT_ENVIRONMENT_ID, fixture.store.activeEnvironmentId())
    }

    @Test
    fun corruptedActivePointerFallsBackOnlyToDefault() {
        val fixture = fixture()
        fixture.store.ensureInitialized()
        commitEnvironmentHead(fixture.store, ENV_A)
        File(fixture.root, "active-environment.json").writeText(
            JSONObject()
                .put("schema", "kite_proot_view_active_environment_v1")
                .put("containerId", "ubuntu-main")
                .put("environmentId", "missing-environment")
                .toString(2)
        )

        val restarted = fixture.newStore("active-recover")
        assertEquals(ProotViewStore.DEFAULT_ENVIRONMENT_ID, restarted.activeEnvironmentId())
        val repaired = JSONObject(File(fixture.root, "active-environment.json").readText())
        assertEquals(ProotViewStore.DEFAULT_ENVIRONMENT_ID, repaired.getString("environmentId"))
    }

    @Test
    fun commitOnEnvironmentADoesNotAdvanceEnvironmentB() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)

        val aChild = fixture.store.prepare("a-first", environmentId = ENV_A)
        fixture.store.verify(aChild.viewId)
        fixture.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        fixture.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        fixture.store.releaseLease(aChild.viewId, "owner-a")

        val bChild = fixture.store.prepare("b-first", environmentId = ENV_B)
        fixture.store.verify(bChild.viewId)
        fixture.store.acquireLease(bChild.viewId, "owner-b", ProotViewLeaseMode.WRITER)
        fixture.store.commit(bChild.viewId, "owner-b", environmentId = ENV_B)
        fixture.store.releaseLease(bChild.viewId, "owner-b")

        // A 继续推进一代，B 的头和 default 不应改变。
        val aSecond = fixture.store.prepare("a-second", environmentId = ENV_A)
        assertEquals(aChild.viewId, aSecond.parentViewId)
        fixture.store.verify(aSecond.viewId)
        fixture.store.acquireLease(aSecond.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        fixture.store.commit(aSecond.viewId, "owner-a", environmentId = ENV_A)
        fixture.store.releaseLease(aSecond.viewId, "owner-a")

        val currents = fixture.store.environmentCurrents()
        assertEquals(aSecond.viewId, currents[ENV_A])
        assertEquals(bChild.viewId, currents[ENV_B])
        assertEquals(base.viewId, currents[ProotViewStore.DEFAULT_ENVIRONMENT_ID])
        assertEquals(base.viewId, fixture.store.currentBinding().viewId)
    }

    @Test
    fun restoreParentOnEnvironmentADoesNotChangeEnvironmentB() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)

        val aChild = fixture.store.prepare("a-update", environmentId = ENV_A)
        fixture.store.verify(aChild.viewId)
        fixture.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        fixture.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        fixture.store.releaseLease(aChild.viewId, "owner-a")

        val aSecond = fixture.store.prepare("a-second", environmentId = ENV_A)
        fixture.store.verify(aSecond.viewId)
        fixture.store.acquireLease(aSecond.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        fixture.store.commit(aSecond.viewId, "owner-a", environmentId = ENV_A)

        val bChild = fixture.store.prepare("b-update", environmentId = ENV_B)
        fixture.store.verify(bChild.viewId)
        fixture.store.acquireLease(bChild.viewId, "owner-b", ProotViewLeaseMode.WRITER)
        fixture.store.commit(bChild.viewId, "owner-b", environmentId = ENV_B)

        // 修复 A 的第二代回到 A 自己的根，B 与 default 均不变。
        fixture.store.restoreParent(aSecond.viewId, "owner-a", environmentId = ENV_A)
        fixture.store.releaseLease(aSecond.viewId, "owner-a")

        val currents = fixture.store.environmentCurrents()
        assertEquals(aChild.viewId, currents[ENV_A])
        assertEquals(bChild.viewId, currents[ENV_B])
        assertEquals(base.viewId, currents[ProotViewStore.DEFAULT_ENVIRONMENT_ID])
    }

    @Test
    fun crossEnvironmentCommitIsRejected() {
        val fixture = fixture()
        fixture.store.ensureInitialized()
        // B 的子 View 不能提交成 A 的头。
        val bChild = fixture.store.prepare("b-only", environmentId = ENV_B)
        fixture.store.verify(bChild.viewId)
        fixture.store.acquireLease(bChild.viewId, "owner-b", ProotViewLeaseMode.WRITER)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.commit(bChild.viewId, "owner-b", environmentId = ENV_A)
        }
        fixture.store.releaseLease(bChild.viewId, "owner-b")
    }

    @Test
    fun sharedHeadAcrossEnvironmentsIsRejected() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        // base 已是 default 头，不能同时登记为 A 的头（A 提交需要 A 的子代）。
        fixture.store.acquireLease(base.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.commit(base.viewId, "owner-a", environmentId = ENV_A)
        }
        fixture.store.releaseLease(base.viewId, "owner-a")
    }

    @Test
    fun illegalEnvironmentIdsFailClosed() {
        val fixture = fixture()
        fixture.store.ensureInitialized()
        listOf("", "   ", "env with space", "env/path", "env\$inject", "a.b", "env:id")
            .plus("x".repeat(65))
            .forEach { illegal ->
                assertThrows(IllegalArgumentException::class.java) {
                    fixture.store.prepare("illegal", environmentId = illegal)
                }.let { /* 仅断言抛出 */ }
            }
    }

    @Test
    fun discardRefusesEnvironmentHeadAndBranchView() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        val aChild = fixture.store.prepare("a-update", environmentId = ENV_A)
        fixture.store.verify(aChild.viewId)
        fixture.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        fixture.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        fixture.store.releaseLease(aChild.viewId, "owner-a")

        // default 头和 A 头都不能直接废弃。
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.discard(base.viewId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.discard(aChild.viewId)
        }
    }

    @Test
    fun legacySingleCurrentMigratesIdempotentlyToDefaultEnvironment() {
        val fixture = fixture()
        val initial = requireNotNull(fixture.store.ensureInitialized().current)
        // 写一份旧式 catalog：没有 environmentCurrents，只有全局 current。
        writeLegacyCatalog(fixture.root, initial.viewId)

        // 首次迁移：旧 current 成为 default 头。
        val firstRecover = fixture.newStore("migrate-1").recover()
        assertEquals(initial.viewId, firstRecover?.currentViewId)
        assertEquals(
            initial.viewId,
            firstRecover?.environmentCurrents?.get(ProotViewStore.DEFAULT_ENVIRONMENT_ID)
        )
        assertTrue(File(fixture.root, "environments.json").exists())

        // 重复迁移：结果稳定，不产生第二份 default 头。
        val secondRecover = fixture.newStore("migrate-2").recover()
        assertEquals(firstRecover, secondRecover)
    }

    @Test
    fun migrationResumesAfterInterruptedEnvironmentsWrite() {
        val fixture = fixture()
        val initial = requireNotNull(fixture.store.ensureInitialized().current)
        writeLegacyCatalog(fixture.root, initial.viewId)
        // 模拟迁移中途崩溃：catalog 已含 environmentCurrents，但 environments.json 未落盘。
        val migratedCatalog = JSONObject(File(fixture.root, "catalog.json").readText())
        migratedCatalog.put("environmentCurrents", JSONObject().put(
            ProotViewStore.DEFAULT_ENVIRONMENT_ID, initial.viewId
        ))
        File(fixture.root, "catalog.json").writeText(migratedCatalog.toString(2))
        File(fixture.root, "environments.json").delete()

        val recovered = fixture.newStore("resume").recover()
        assertEquals(initial.viewId, recovered?.currentViewId)
        assertEquals(
            initial.viewId,
            recovered?.environmentCurrents?.get(ProotViewStore.DEFAULT_ENVIRONMENT_ID)
        )
        assertTrue(File(fixture.root, "environments.json").exists())
    }

    @Test
    fun environmentPointersPersistAcrossStoreRestart() {
        val fixture = fixture()
        fixture.store.ensureInitialized()
        val aChild = fixture.store.prepare("a-update", environmentId = ENV_A)
        fixture.store.verify(aChild.viewId)
        fixture.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        fixture.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        fixture.store.releaseLease(aChild.viewId, "owner-a")

        val restarted = fixture.newStore("restart-process")
        val recovered = requireNotNull(restarted.recover())
        assertEquals(aChild.viewId, recovered.environmentCurrents[ENV_A])
        assertEquals(aChild.viewId, restarted.currentBinding(ENV_A).viewId)
        // default 头在重启后仍可读取（current.json 与 environments.json 一致）。
        assertNotNull(restarted.currentBinding().viewId)
    }

    @Test
    fun defaultEnvironmentCommitUpdatesCurrentJsonForBackwardCompatibility() {
        val fixture = fixture()
        val initial = requireNotNull(fixture.store.ensureInitialized().current)
        val child = fixture.store.prepare("default-update")
        fixture.store.verify(child.viewId)
        fixture.store.acquireLease(child.viewId, "owner-default", ProotViewLeaseMode.WRITER)
        fixture.store.commit(child.viewId, "owner-default")
        fixture.store.releaseLease(child.viewId, "owner-default")

        val currentJson = JSONObject(File(fixture.root, "current.json").readText())
        assertEquals(child.viewId, currentJson.getString("viewId"))
        val envsJson = JSONObject(File(fixture.root, "environments.json").readText())
        assertEquals(
            child.viewId,
            envsJson.getJSONObject("environments").getString(ProotViewStore.DEFAULT_ENVIRONMENT_ID)
        )
        // 旧字段 currentViewId 也指向 default 头，保证旧读取方不漂移。
        val catalogJson = JSONObject(File(fixture.root, "catalog.json").readText())
        assertEquals(child.viewId, catalogJson.optString("currentViewId"))
        // 只提交 default 时，不应产生其他环境头。
        val envObj = catalogJson.optJSONObject("environmentCurrents")
        assertEquals(
            child.viewId,
            envObj?.optString(ProotViewStore.DEFAULT_ENVIRONMENT_ID)
        )
        assertEquals("", envObj?.optString(ENV_A))
        // initial 仍可作为 default 的祖先引用。
        assertEquals(initial.viewId, fixture.store.currentBinding().parentViewIds.first())
    }

    @Test
    fun ancestorCycleFailsClosedByBreakingLoopMembers() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        val child = fixture.store.prepare("child-update")
        // 篡改 catalog，让 child 的父代指向自己，形成自循环。
        val original = JSONObject(File(fixture.root, "catalog.json").readText())
        val views = original.getJSONArray("views")
        for (index in 0 until views.length()) {
            val view = views.getJSONObject(index)
            if (view.getString("viewId") == child.viewId) {
                view.put("parentViewId", child.viewId)
            }
        }
        original.put("views", views)
        File(fixture.root, "catalog.json").writeText(original.toString(2))

        val recovered = requireNotNull(fixture.newStore("cycle-process").recover())
        // 自循环 child 必须被降级，不能成为任何环境头；default 回到 base。
        val brokenChild = recovered.views.first { it.viewId == child.viewId }
        assertEquals(ProotViewState.BROKEN, brokenChild.state)
        assertEquals(
            base.viewId,
            recovered.environmentCurrents[ProotViewStore.DEFAULT_ENVIRONMENT_ID]
        )
    }

    @Test
    fun brokenEnvironmentHeadNeverFallsBackToOtherEnvironment() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        // A、B 各自从 Base 派生独立头，并各推进一代。
        val aFirst = commitEnvironmentHead(fixture.store, ENV_A)
        val bFirst = commitEnvironmentHead(fixture.store, ENV_B)
        val aSecond = commitEnvironmentHead(fixture.store, ENV_A)
        val bSecond = commitEnvironmentHead(fixture.store, ENV_B)

        // 损坏 A 的当前头（删除 control.conf），模拟 A 头失效。B 头保持完好。
        val aHeadRecord = fixture.store.recover()!!.views.first { it.viewId == aSecond }
        File(aHeadRecord.controlFilePath).delete()

        val recovered = requireNotNull(fixture.newStore("recover-process").recover())
        val currents = recovered.environmentCurrents
        // B 头和 default 头完全不受影响。
        assertEquals(bSecond, currents[ENV_B])
        assertEquals(base.viewId, currents[ProotViewStore.DEFAULT_ENVIRONMENT_ID])
        // A 绝不指向 B 的任何头，也不指向 default。A 要么回退到自己的合法祖先（aFirst），
        // 要么因整条链失效而被移除；两种情况都不允许借用 B。
        val recoveredA = currents[ENV_A]
        if (recoveredA != null) {
            assertNotEquals(bFirst, recoveredA)
            assertNotEquals(bSecond, recoveredA)
            assertNotEquals(base.viewId, recoveredA)
            // A 只能落在自己的祖先链上。
            assertTrue(recoveredA == aSecond || recoveredA == aFirst)
        }
    }

    @Test
    fun brokenEnvironmentHeadWithIntactAncestorFallsBackToOwnAncestorOnly() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        val aFirst = commitEnvironmentHead(fixture.store, ENV_A)
        val bFirst = commitEnvironmentHead(fixture.store, ENV_B)
        val aSecond = commitEnvironmentHead(fixture.store, ENV_A)

        // 仅损坏 A 的二代头；A 的一代头 aFirst 仍完好。
        val aHeadRecord = fixture.store.recover()!!.views.first { it.viewId == aSecond }
        File(aHeadRecord.controlFilePath).delete()

        val recovered = requireNotNull(fixture.newStore("recover-ancestor").recover())
        // A 回退到自己的合法祖先 aFirst，绝不指向 B 或 default。
        assertEquals(aFirst, recovered.environmentCurrents[ENV_A])
        assertEquals(bFirst, recovered.environmentCurrents[ENV_B])
        assertEquals(base.viewId, recovered.environmentCurrents[ProotViewStore.DEFAULT_ENVIRONMENT_ID])
    }

    @Test
    fun fullyBrokenEnvironmentIsRemovedRatherThanBorrowingOtherHead() {
        val fixture = fixture()
        val base = requireNotNull(fixture.store.ensureInitialized().current)
        val aHead = commitEnvironmentHead(fixture.store, ENV_A)
        val bHead = commitEnvironmentHead(fixture.store, ENV_B)

        // 损坏 A 的唯一头及其父链入口（aHead 的 control 与 base 投影无关，但 aHead 是 A 唯一头）。
        // 这里把 A 头和 B 头都指向各自的 view；只破坏 A 头，并确保 A 没有其他祖先可回退
        // （aHead 的父代是 base 投影，base 属于 default，A 不能借用）。
        val aHeadRecord = fixture.store.recover()!!.views.first { it.viewId == aHead }
        File(aHeadRecord.controlFilePath).delete()
        // 进一步把 environments.json 的 A 指向一个不存在的 view，确保指针也失效。
        val envsJson = JSONObject(File(fixture.root, "environments.json").readText())
        envsJson.getJSONObject("environments").put(ENV_A, "view-nonexistent")
        File(fixture.root, "environments.json").writeText(envsJson.toString(2))

        val recovered = requireNotNull(fixture.newStore("recover-removed").recover())
        // A 无法从自己的指针/祖先恢复时被移除，绝不借用 B 或 default。
        val currents = recovered.environmentCurrents
        assertFalse(currents.containsKey(ENV_A))
        assertEquals(bHead, currents[ENV_B])
        assertEquals(base.viewId, currents[ProotViewStore.DEFAULT_ENVIRONMENT_ID])
    }

    private fun commitEnvironmentHead(
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

    private fun writeLegacyCatalog(root: File, currentViewId: String) {
        // 读取现有 catalog，删除 environmentCurrents，保留旧式全局 current。
        val catalog = JSONObject(File(root, "catalog.json").readText())
        catalog.remove("environmentCurrents")
        catalog.put("currentViewId", currentViewId)
        File(root, "catalog.json").writeText(catalog.toString(2))
        File(root, "environments.json").delete()
    }

    private fun fixture(): StoreFixture {
        val base = temporaryFolder.newFolder("env-base-${System.nanoTime()}")
        val root = temporaryFolder.newFolder("env-store-${System.nanoTime()}")
        var clock = 20_000L
        var sequence = 0
        fun createStore(processSessionId: String = "env-fixture-process") = ProotViewStore(
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
