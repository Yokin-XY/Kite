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
    fun ensure_removesLegacyToolchainServiceShimsButPreservesCustomCommands() {
        val workspace = Files.createTempDirectory("kite-service-shim-migration-").toFile()
        try {
            val helperBin = WorkspaceBuildSupport.helperBinDir(workspace).also { it.mkdirs() }
            val systemctl = helperBin.resolve("systemctl").apply {
                writeText(
                    "#!/usr/bin/env sh\n" +
                        "echo 'KFShell runs Android/proot without systemd. Use KFShell runtime controls instead.'\n"
                )
            }
            val service = helperBin.resolve("service").apply {
                writeText(
                    "#!/usr/bin/env sh\n" +
                        "echo 'KFShell does not provide SysV/systemd service management. Use KFShell runtime controls instead.'\n"
                )
            }
            val custom = helperBin.resolve("custom-service-command").apply {
                writeText("#!/usr/bin/env sh\necho custom\n")
            }

            WorkspaceBuildSupport.migrateLegacyEnvBinIfNeeded(workspace, sealed = false)

            assertFalse(systemctl.exists())
            assertFalse(service.exists())
            assertTrue(custom.isFile)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun ensure_writesVersionedSupervisordHealthHelperWithoutArguments() {
        val workspace = Files.createTempDirectory("kite-supervisord-health-helper-").toFile()
        try {
            val helper = WorkspaceBuildSupport.ensureSupervisordHealthSnapshotHelper(workspace)
            val script = helper.readText()

            assertEquals(
                WorkspaceBuildSupport.CONTAINER_SUPERVISORD_HEALTH_SNAPSHOT_PATH.substringAfterLast('/'),
                helper.name,
            )
            assertTrue(script.contains("KF_GENERATED_SUPERVISORD_HEALTH_SNAPSHOT_VERSION=1"))
            assertTrue(script.contains("if [ \"${'$'}#\" -ne 0 ]"))
            assertTrue(script.contains("/usr/bin/supervisorctl"))
            assertTrue(script.contains(" update "))
            assertTrue(script.contains(" status 2>&1"))
            assertTrue(script.contains("__KF_SUPERVISOR_LOGS__"))
            assertTrue(script.contains("/usr/bin/tail -n 8"))
        } finally {
            workspace.deleteRecursively()
        }
    }

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
