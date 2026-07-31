package com.kite.app.foundation.workspace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.RuntimeExecutionPayload
import com.kite.app.foundation.runtime.RuntimeExecutionRequest
import com.kite.app.foundation.runtime.RuntimeExecutionRequirement
import com.kite.app.foundation.runtime.RuntimeFallbackPolicy
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManagedRuntimeLaunchPlannerTest {
    @Test
    fun `python child requirement falls back before preparing host assets`() {
        val fixture = fixture()

        val plan = ManagedRuntimeLaunchPlanner.plan(
            context = ApplicationProvider.getApplicationContext<Context>(),
            container = fixture.container,
            workspaceDirectory = fixture.workspace,
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv("python3", listOf("task.py")),
                requirements = setOf(RuntimeExecutionRequirement.CHILD_PROCESS),
            ),
        )

        assertEquals(
            "python_child_process_required",
            (plan as ManagedRuntimeLaunchPlan.Fallback).reason,
        )
    }

    @Test
    fun `disabled fallback turns unsupported python capability into blocked plan`() {
        val fixture = fixture()

        val plan = ManagedRuntimeLaunchPlanner.plan(
            context = ApplicationProvider.getApplicationContext<Context>(),
            container = fixture.container,
            workspaceDirectory = fixture.workspace,
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv("python3", listOf("-m", "venv", "/tmp/test")),
                fallbackPolicy = RuntimeFallbackPolicy.DISABLED,
            ),
        )

        assertEquals(
            "fallback_disabled:python_venv_requires_proot",
            (plan as ManagedRuntimeLaunchPlan.Blocked).reason,
        )
    }

    @Test
    fun `python without affirmative host guarantees falls back before asset preparation`() {
        val fixture = fixture()

        val plan = ManagedRuntimeLaunchPlanner.plan(
            context = ApplicationProvider.getApplicationContext<Context>(),
            container = fixture.container,
            workspaceDirectory = fixture.workspace,
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv("python3", listOf("task.py")),
            ),
        )

        assertEquals(
            "python_no_child_process_guarantee_missing",
            (plan as ManagedRuntimeLaunchPlan.Fallback).reason,
        )
    }

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("kite-managed-planner-test").toFile()
        val rootfs = root.resolve("rootfs").apply { mkdirs() }
        val workspace = root.resolve("workspace").apply { mkdirs() }
        return Fixture(
            workspace = workspace,
            container = ContainerRecord(
                id = "test",
                displayName = "Test",
                imageName = "ubuntu",
                rootfsPath = rootfs.absolutePath,
                workspacePath = workspace.absolutePath,
                createdAt = 1L,
            ),
        )
    }

    private data class Fixture(
        val workspace: java.io.File,
        val container: ContainerRecord,
    )
}
