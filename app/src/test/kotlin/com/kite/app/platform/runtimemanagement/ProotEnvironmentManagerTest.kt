package com.kite.app.platform.runtimemanagement

import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.ProotEnvironmentWorkspace
import com.kite.app.foundation.runtime.ProotViewStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProotEnvironmentManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createBuildsIndependentRootAndPrivateWorkspaceWithoutSwitchingActive() {
        val fixture = fixture()

        val created = fixture.manager.createEnvironment(ENV_A).getOrThrow()

        assertEquals(ENV_A, created.environmentId)
        assertTrue(created.parentViewIds.isEmpty())
        assertEquals(ProotViewStore.DEFAULT_ENVIRONMENT_ID, fixture.store.activeEnvironmentId())
        assertTrue(
            ProotEnvironmentWorkspace.plan(fixture.container, created).workspaceDirectory.isDirectory
        )
    }

    @Test
    fun switchQuiescesOldViewThenChangesActiveEnvironment() {
        val calls = mutableListOf<String>()
        val fixture = fixture(quiesce = { viewId ->
            calls += viewId
            Result.success(Unit)
        })
        val defaultView = fixture.store.currentBinding().viewId
        fixture.manager.createEnvironment(ENV_A).getOrThrow()

        val switched = fixture.manager.switchEnvironment(ENV_A).getOrThrow()

        assertEquals(listOf(defaultView), calls)
        assertEquals(ENV_A, switched.environmentId)
        assertEquals(ENV_A, fixture.store.activeEnvironmentId())
        assertFalse(fixture.store.recover()!!.views.first { it.viewId == defaultView }.leases.isNotEmpty())
    }

    @Test
    fun switchReportsOneSummaryWithAllCriticalPhases() {
        val metrics = mutableListOf<ProotEnvironmentSwitchMetrics>()
        var nowNanos = 0L
        val fixture = fixture(
            monotonicNanos = {
                nowNanos += 1_000_000L
                nowNanos
            },
            onSwitchMeasured = metrics::add,
        )
        fixture.manager.createEnvironment(ENV_A).getOrThrow()

        fixture.manager.switchEnvironment(ENV_A).getOrThrow()

        assertEquals(1, metrics.size)
        metrics.single().let { measured ->
            assertEquals(ProotViewStore.DEFAULT_ENVIRONMENT_ID, measured.sourceEnvironmentId)
            assertEquals(ENV_A, measured.targetEnvironmentId)
            assertTrue(measured.changed)
            assertTrue(measured.success)
            assertTrue(measured.preparationMs > 0L)
            assertTrue(measured.quiesceMs > 0L)
            assertTrue(measured.pointerSwitchMs > 0L)
            assertTrue(measured.totalMs >= measured.preparationMs + measured.quiesceMs + measured.pointerSwitchMs)
            assertTrue(measured.error.isBlank())
        }
    }

    @Test
    fun quiesceFailureKeepsOldEnvironmentActiveAndReleasesSwitchLease() {
        val fixture = fixture(quiesce = { Result.failure(IllegalStateException("still-running")) })
        fixture.manager.createEnvironment(ENV_A).getOrThrow()
        val defaultView = fixture.store.currentBinding().viewId

        val failure = fixture.manager.switchEnvironment(ENV_A).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("still-running"))
        assertEquals(ProotViewStore.DEFAULT_ENVIRONMENT_ID, fixture.store.activeEnvironmentId())
        assertTrue(fixture.store.currentBinding().viewId == defaultView)
        assertTrue(fixture.store.recover()!!.views.first { it.viewId == defaultView }.leases.isEmpty())
    }

    @Test
    fun duplicateEnvironmentFailsWithoutCreatingExtraView() {
        val fixture = fixture()
        fixture.manager.createEnvironment(ENV_A).getOrThrow()
        val before = fixture.store.recover()!!.views.map { it.viewId }.toSet()

        val failure = fixture.manager.createEnvironment(ENV_A).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("已存在"))
        assertEquals(before, fixture.store.recover()!!.views.map { it.viewId }.toSet())
        assertNull(fixture.store.recover()!!.views.firstOrNull { it.purpose == "environment-create:$ENV_A" && it.viewId !in before })
    }

    private fun fixture(
        quiesce: (String) -> Result<Unit> = { Result.success(Unit) },
        monotonicNanos: () -> Long = System::nanoTime,
        onSwitchMeasured: (ProotEnvironmentSwitchMetrics) -> Unit = {},
    ): Fixture {
        val runtime = temporaryFolder.newFolder("manager-runtime-${System.nanoTime()}")
        val rootfs = File(runtime, "containers/ubuntu-main/rootfs").apply { mkdirs() }
        val workspace = File(runtime, "shared/ubuntu-main").apply { mkdirs() }
        File(workspace, ".kf").mkdirs()
        val container = ContainerRecord(
            id = "ubuntu-main",
            displayName = "Ubuntu",
            imageName = "ubuntu-base",
            rootfsPath = rootfs.absolutePath,
            workspacePath = workspace.absolutePath,
            createdAt = 1L,
        )
        val store = ProotViewStore.forContainer(container)
        store.ensureInitialized()
        store.enable()
        var ownerSequence = 0
        val manager = ProotEnvironmentManager(
            containerProvider = { container },
            storeProvider = { store },
            quiesceView = quiesce,
            ownerIdFactory = { operation -> "$operation-${++ownerSequence}" },
            monotonicNanos = monotonicNanos,
            onSwitchMeasured = onSwitchMeasured,
        )
        return Fixture(container, store, manager)
    }

    private data class Fixture(
        val container: ContainerRecord,
        val store: ProotViewStore,
        val manager: ProotEnvironmentManager,
    )

    companion object {
        private const val ENV_A = "profile_a"
    }
}
