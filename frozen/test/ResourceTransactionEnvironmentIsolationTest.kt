package com.kite.app.platform.resources

import com.kite.app.foundation.runtime.ProotViewBinding
import com.kite.app.foundation.runtime.ProotViewStore
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
 * T013d 引导事务环境隔离验收：A 的提交或失败只改 A 头，B 和 default 不受影响。
 *
 * 这里验证资源事务适配器与多环境 View 的协作；View 头隔离本身由 ProotViewStoreEnvironmentTest
 * 覆盖。生产中的同一个 coordinator 必须能同时保存不同环境的活动事务。
 */
@RunWith(RobolectricTestRunner::class)
class ResourceTransactionEnvironmentIsolationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun updateOnEnvironmentAAdvancesOnlyEnvironmentAHead() {
        val fixture = fixture()
        fixture.viewStore.ensureInitialized()
        val defaultHead = fixture.viewStore.currentBinding().viewId
        // 先为 A、B 各自建立直接覆盖同一 Base 的独立根头。
        val aHead = commitEnvironmentHead(fixture.viewStore, ENV_A)
        val bHead = commitEnvironmentHead(fixture.viewStore, ENV_B)

        fixture.coordinatorA.beginUpdate(
            RESOURCE_ID, RUN_A, "1.0.0", "2.0.0", environmentId = ENV_A
        ).getOrThrow()
        val aChild = fixture.coordinatorA.environmentForRun(RUN_A)
            .getValue(ProotViewBinding.ENV_VIEW_ID)
        assertNotEquals(aHead, aChild)
        fixture.coordinatorA.commitUpdate(RESOURCE_ID, RUN_A).getOrThrow()
        fixture.coordinatorA.finalizeUpdate(RESOURCE_ID, RUN_A).getOrThrow()

        // A 头推进到子 View；default 和 B 头不变。
        assertEquals(aChild, fixture.viewStore.currentBinding(ENV_A).viewId)
        assertEquals(bHead, fixture.viewStore.currentBinding(ENV_B).viewId)
        assertEquals(defaultHead, fixture.viewStore.currentBinding().viewId)
    }

    @Test
    fun updateWithoutExplicitEnvironmentFollowsActiveEnvironment() {
        val fixture = fixture()
        fixture.viewStore.ensureInitialized()
        val defaultHead = fixture.viewStore.currentBinding().viewId
        val aHead = commitEnvironmentHead(fixture.viewStore, ENV_A)
        fixture.viewStore.switchActiveEnvironment(ENV_A)

        fixture.coordinatorDefault.beginUpdate(
            RESOURCE_ID, RUN_DEFAULT, "1.0.0", "2.0.0"
        ).getOrThrow()
        val environment = fixture.coordinatorDefault.environmentForRun(RUN_DEFAULT)

        assertEquals(ENV_A, environment[ProotViewBinding.ENV_ENVIRONMENT_ID])
        assertEquals(defaultHead, fixture.viewStore.currentBinding().viewId)
        fixture.coordinatorDefault.rollbackUpdate(RESOURCE_ID, RUN_DEFAULT).getOrThrow()
        assertEquals(aHead, fixture.viewStore.currentBinding(ENV_A).viewId)
    }

    @Test
    fun rollbackOnEnvironmentADoesNotTouchEnvironmentB() {
        val fixture = fixture()
        fixture.viewStore.ensureInitialized()
        val defaultHead = fixture.viewStore.currentBinding().viewId
        val aHead = commitEnvironmentHead(fixture.viewStore, ENV_A)
        val bHead = commitEnvironmentHead(fixture.viewStore, ENV_B)

        fixture.coordinatorA.beginUpdate(
            RESOURCE_ID, RUN_A, "1.0.0", "2.0.0", environmentId = ENV_A
        ).getOrThrow()
        val aChild = fixture.coordinatorA.environmentForRun(RUN_A)
            .getValue(ProotViewBinding.ENV_VIEW_ID)
        fixture.coordinatorA.rollbackUpdate(RESOURCE_ID, RUN_A).getOrThrow()

        // A 失败回滚：A 的子 View 被回收，A 头回到 aHead；B 和 default 头不变。
        assertFalse(fixture.viewStore.recover()!!.views.any { it.viewId == aChild })
        assertEquals(aHead, fixture.viewStore.currentBinding(ENV_A).viewId)
        assertEquals(bHead, fixture.viewStore.currentBinding(ENV_B).viewId)
        assertEquals(defaultHead, fixture.viewStore.currentBinding().viewId)
    }

    @Test
    fun interruptedRecoveryOnEnvironmentADoesNotAffectEnvironmentB() {
        val fixture = fixture()
        fixture.viewStore.ensureInitialized()
        val defaultHead = fixture.viewStore.currentBinding().viewId
        val aHead = commitEnvironmentHead(fixture.viewStore, ENV_A)
        val bHead = commitEnvironmentHead(fixture.viewStore, ENV_B)

        // A beginUpdate 后模拟应用中断（不 commit），重启后恢复。
        fixture.coordinatorA.beginUpdate(
            RESOURCE_ID, RUN_A, "1.0.0", "2.0.0", environmentId = ENV_A
        ).getOrThrow()
        val restarted = freshCoordinator(fixture, fixture.storeRootA, "restart-a")
        val recovery = restarted.recoverInterruptedTransactions().first { it.resourceId == RESOURCE_ID }

        assertTrue(recovery.restored)
        assertEquals(aHead, fixture.viewStore.currentBinding(ENV_A).viewId)
        assertEquals(bHead, fixture.viewStore.currentBinding(ENV_B).viewId)
        assertEquals(defaultHead, fixture.viewStore.currentBinding().viewId)
    }

    @Test
    fun recordPersistsEnvironmentIdAcrossRestart() {
        val fixture = fixture()
        fixture.viewStore.ensureInitialized()
        commitEnvironmentHead(fixture.viewStore, ENV_A)
        fixture.coordinatorA.beginUpdate(
            RESOURCE_ID, RUN_A, "1.0.0", "2.0.0", environmentId = ENV_A
        ).getOrThrow()
        // 读取落盘的 record，确认 environmentId 持久化。
        val record = restartedRecord(fixture.storeRootA)
        assertEquals(ENV_A, record.optString("environmentId"))
    }

    @Test
    fun oneCoordinatorKeepsActiveTransactionsSeparatedByEnvironment() {
        val fixture = fixture()
        fixture.viewStore.ensureInitialized()
        commitEnvironmentHead(fixture.viewStore, ENV_A)
        commitEnvironmentHead(fixture.viewStore, ENV_B)

        fixture.coordinatorDefault.beginUpdate(
            RESOURCE_ID, RUN_A, "1", "2", environmentId = ENV_A
        ).getOrThrow()
        fixture.coordinatorDefault.beginUpdate(
            RESOURCE_ID, RUN_B, "1", "2", environmentId = ENV_B
        ).getOrThrow()

        assertEquals(
            ENV_A,
            fixture.coordinatorDefault.environmentForRun(RUN_A)[ProotViewBinding.ENV_ENVIRONMENT_ID]
        )
        assertEquals(
            ENV_B,
            fixture.coordinatorDefault.environmentForRun(RUN_B)[ProotViewBinding.ENV_ENVIRONMENT_ID]
        )
        fixture.coordinatorDefault.rollbackUpdate(RESOURCE_ID, RUN_A).getOrThrow()
        fixture.coordinatorDefault.rollbackUpdate(RESOURCE_ID, RUN_B).getOrThrow()
    }

    @Test
    fun legacyRecordWithoutEnvironmentIdTreatedAsDefault() {
        val fixture = fixture()
        fixture.viewStore.ensureInitialized()
        // 写一份旧式 record（无 environmentId），重启后应按 default 恢复而不崩溃。
        fixture.coordinatorDefault.beginUpdate(
            RESOURCE_ID, RUN_DEFAULT, "1.0.0", "2.0.0"
        ).getOrThrow()
        // 手动抹掉 environmentId 字段，模拟旧记录。
        val activeRecordFile = File(fixture.storeRootDefault, "active/default.json")
        val recordFile = File(fixture.storeRootDefault, "resource-view-transaction.json")
        activeRecordFile.copyTo(recordFile)
        activeRecordFile.delete()
        val json = org.json.JSONObject(recordFile.readText())
        json.remove("environmentId")
        recordFile.writeText(json.toString(2))

        val restarted = freshCoordinator(fixture, fixture.storeRootDefault, "restart-legacy")
        // 旧记录按 default 恢复，不抛异常。
        val recovery = restarted.recoverInterruptedTransactions()
        assertTrue(recovery.any { it.resourceId == RESOURCE_ID })
    }

    private fun commitEnvironmentHead(
        store: ProotViewStore,
        environmentId: String
    ): String {
        val child = store.prepare("env-head-$environmentId", environmentId = environmentId)
        store.verify(child.viewId)
        store.acquireLease(child.viewId, "env-owner-$environmentId", com.kite.app.foundation.runtime.ProotViewLeaseMode.WRITER)
        store.commit(child.viewId, "env-owner-$environmentId", environmentId = environmentId)
        store.releaseLease(child.viewId, "env-owner-$environmentId")
        return child.viewId
    }

    private fun restartedRecord(storeRoot: File): org.json.JSONObject {
        val recordFile = File(storeRoot, "active/$ENV_A.json")
        return org.json.JSONObject(recordFile.readText())
    }

    private fun fixture(): Fixture {
        val root = temporaryFolder.newFolder()
        val baseRoot = File(root, "runtime").also(File::mkdirs)
        val managedSoftware = File(baseRoot, "shared/default/.kf/software").also(File::mkdirs)
        val installRoot = File(managedSoftware, RESOURCE_ID).also(File::mkdirs)
        val viewRoot = File(root, "views")
        val controlFile = File(root, "legacy/kf-resource-transaction.active")
        var clock = 1_000L
        var sequence = 0
        val viewStoreProvider: () -> ProotViewStore? = {
            ProotViewStore(
                rootDirectory = viewRoot,
                containerId = "ubuntu-main",
                baseRootDirectory = baseRoot,
                scopeRootDirectories = listOf(managedSoftware),
                now = { ++clock },
                idFactory = { "id-${++sequence}" },
                processSessionId = "env-isolation-process"
            )
        }
        val viewStore = viewStoreProvider()!!
        val storeRootA = File(root, "tx-a")
        val storeRootB = File(root, "tx-b")
        val storeRootDefault = File(root, "tx-default")
        fun create(storeRoot: File, store: ProotViewStore = viewStore) =
            ResourceTransactionCoordinator(
                storeRoot = storeRoot,
                controlFile = controlFile,
                targetResolver = { ResourceTransactionTarget(it, installRoot) },
                viewStoreProvider = { store },
                now = { ++clock },
                nativePath = File::getAbsolutePath
            )
        return Fixture(
            viewStore = viewStore,
            baseRoot = baseRoot,
            managedSoftware = managedSoftware,
            viewRoot = viewRoot,
            storeRootA = storeRootA,
            storeRootB = storeRootB,
            storeRootDefault = storeRootDefault,
            clock = { ++clock },
            sequence = { ++sequence },
            coordinatorA = create(storeRootA),
            coordinatorB = create(storeRootB),
            coordinatorDefault = create(storeRootDefault)
        )
    }

    private fun freshCoordinator(
        fixture: Fixture,
        storeRoot: File,
        session: String
    ): ResourceTransactionCoordinator {
        val freshStore = ProotViewStore(
            rootDirectory = fixture.viewRoot,
            containerId = "ubuntu-main",
            baseRootDirectory = fixture.baseRoot,
            scopeRootDirectories = listOf(fixture.managedSoftware),
            now = fixture.clock,
            idFactory = { "id-${fixture.sequence()}" },
            processSessionId = session
        )
        return ResourceTransactionCoordinator(
            storeRoot = storeRoot,
            controlFile = File(fixture.storeRootA.parentFile, "legacy/kf-resource-transaction.active"),
            targetResolver = { ResourceTransactionTarget(it, File(fixture.managedSoftware, RESOURCE_ID)) },
            viewStoreProvider = { freshStore },
            now = fixture.clock,
            nativePath = File::getAbsolutePath
        )
    }

    private data class Fixture(
        val viewStore: ProotViewStore,
        val baseRoot: File,
        val managedSoftware: File,
        val viewRoot: File,
        val storeRootA: File,
        val storeRootB: File,
        val storeRootDefault: File,
        val clock: () -> Long,
        val sequence: () -> Int,
        val coordinatorA: ResourceTransactionCoordinator,
        val coordinatorB: ResourceTransactionCoordinator,
        val coordinatorDefault: ResourceTransactionCoordinator
    )

    companion object {
        private const val RESOURCE_ID = "kite.test.resource"
        private const val ENV_A = "env-a"
        private const val ENV_B = "env-b"
        private const val RUN_A = "resource-run-a"
        private const val RUN_B = "resource-run-b"
        private const val RUN_DEFAULT = "resource-run-default"
    }
}
