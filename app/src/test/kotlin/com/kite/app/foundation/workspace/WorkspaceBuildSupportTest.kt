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
    fun ensure_writesSystemFdWrapperWithoutMutatingContainerEnvBin() {
        val workspace = Files.createTempDirectory("kite-workspace-support-").toFile()
        try {
            val helperBin = WorkspaceBuildSupport.helperBinDir(workspace).also { it.mkdirs() }
            val fd = helperBin.resolve("fd")
            val legacyTarget = Paths.get("/workspace/.kf/software/kite.tool.env/bin/fd")
            val symlinkCreated = runCatching {
                Files.createSymbolicLink(
                    fd.toPath(),
                    legacyTarget
                )
            }.isSuccess
            assumeTrue("symlink creation is not available on this host", symlinkCreated)

            WorkspaceBuildSupport.ensure(workspace)

            assertTrue(Files.isSymbolicLink(fd.toPath()))
            assertEquals(legacyTarget, Files.readSymbolicLink(fd.toPath()))
            val systemFd = WorkspaceBuildSupport.helperSystemBinDir(workspace).resolve("fd")
            assertFalse(Files.isSymbolicLink(systemFd.toPath()))
            assertTrue(systemFd.readText().contains("exec fdfind"))
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
    fun ensure_projectsDeviceBridgeCapabilityCatalogFromSharedContract() {
        val workspace = Files.createTempDirectory("kite-device-catalog-").toFile()
        try {
            WorkspaceBuildSupport.ensure(workspace)

            val catalog = JSONObject(
                workspace.resolve(".kf/system/device-bridge-capabilities-v1.json").readText()
            )
            val capabilities = catalog.getJSONArray("capabilities")
            assertTrue(capabilities.length() > 10)
            assertEquals(1, catalog.getInt("protocolVersion"))
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun ensure_writesBoundedProbeSupportIntoHostSelfBridge() {
        val workspace = Files.createTempDirectory("kite-device-bridge-timeout-").toFile()
        try {
            WorkspaceBuildSupport.ensure(workspace)

            val script = workspace.resolve(".kf/system/bin/kf-adb-bridge").readText()
            assertTrue(script.contains("KF_ADB_BRIDGE_REQUEST_TIMEOUT_SEC"))
            assertTrue(script.contains("request timed out after"))
            assertTrue(script.contains("return 124"))
            assertTrue(script.contains("head -c ${'$'}((size - stdout_pos))"))
            assertTrue(script.contains("head -c ${'$'}((size - stderr_pos))"))
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun ensure_exposesDeviceBridgeDiscoveryInHostContract() {
        val workspace = Files.createTempDirectory("kite-device-host-contract-").toFile()
        try {
            WorkspaceBuildSupport.ensure(workspace)

            val contract = JSONObject(workspace.resolve(".kf/host-contract.json").readText())
            val bridge = contract.getJSONObject("deviceBridge")
            assertEquals("v1", contract.getString("surfaceVersion"))
            assertEquals("kite-device capabilities --json", bridge.getString("capabilityCommand"))
            assertEquals("adb -s kf-host-self", bridge.getString("compatibilityTarget"))
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
