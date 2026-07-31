package com.kite.app.foundation.workspace

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import java.nio.file.Paths

@RunWith(RobolectricTestRunner::class)
class WorkspaceBuildSupportTest {
    @Test
    fun ensure_replacesContainerSymlinkFdWrapper() {
        val workspace = Files.createTempDirectory("kite-workspace-support-").toFile()
        try {
            val helperBin = WorkspaceBuildSupport.helperBinDir(workspace).also { it.mkdirs() }
            val fd = helperBin.resolve("fd")
            val symlinkCreated = runCatching {
                Files.createSymbolicLink(
                    fd.toPath(),
                    Paths.get("/workspace/.kf/software/kite.tool.env/bin/fd")
                )
            }.isSuccess
            assumeTrue("symlink creation is not available on this host", symlinkCreated)

            WorkspaceBuildSupport.ensure(workspace)

            assertFalse(Files.isSymbolicLink(fd.toPath()))
            assertTrue(fd.readText().contains("exec fdfind"))
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun ensure_keepsCapacityExecutorDisabledUntilRealTaskDispatchExists() {
        val workspace = Files.createTempDirectory("kite-capacity-policy-").toFile()
        try {
            WorkspaceBuildSupport.ensure(workspace)

            val policy = JSONObject(
                WorkspaceBuildSupport.prootCapacityExecutorPolicyFile(workspace).readText()
            )
            assertFalse(policy.getBoolean("enabled"))
            assertEquals("disabled_until_task_dispatch", policy.getString("mode"))
            assertTrue(policy.getString("note").contains("command dispatch"))
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun ensure_disablesPreviouslyGeneratedAutoCapacityPolicy() {
        val workspace = Files.createTempDirectory("kite-capacity-policy-migrate-").toFile()
        try {
            WorkspaceBuildSupport.ensure(workspace)
            val policyFile = WorkspaceBuildSupport.prootCapacityExecutorPolicyFile(workspace)
            val legacyGenerated = JSONObject(policyFile.readText())
                .put("mode", "guarded_auto_bound_workers")
                .put("enabled", true)
                .put(
                    "note",
                    "Default target selection uses the first inactive Android-registered PROOT_CAPACITY_WORKER."
                )
            policyFile.writeText(legacyGenerated.toString(2) + "\n")

            WorkspaceBuildSupport.ensure(workspace)

            val migrated = JSONObject(policyFile.readText())
            assertFalse(migrated.getBoolean("enabled"))
            assertEquals("disabled_until_task_dispatch", migrated.getString("mode"))
        } finally {
            workspace.deleteRecursively()
        }
    }
}
