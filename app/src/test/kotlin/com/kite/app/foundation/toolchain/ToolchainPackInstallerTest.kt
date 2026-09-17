package com.kite.app.foundation.toolchain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolchainPackInstallerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bootstrapRegistration_usesCommandExitCodeAsInstallSignal() {
        val installed = ToolchainPackInstaller.resolveBootstrapResourceRegistration(
            resourceId = "kite.ripgrep",
            version = "rootfs",
            exitCode = 0,
            timedOut = false,
            summary = "SUMMARY PASS=1 WARN=0 FAIL=0"
        )

        assertEquals("kite.ripgrep", installed.resourceId)
        assertTrue(installed.installed)
        assertEquals("rootfs", installed.version)
    }

    @Test
    fun bootstrapRegistration_nonZeroExitIsResourceFailureOnly() {
        val failed = ToolchainPackInstaller.resolveBootstrapResourceRegistration(
            resourceId = "kite.curl",
            version = "rootfs",
            exitCode = 2,
            timedOut = false,
            summary = "missing rootfs command: curl"
        )

        assertFalse(failed.installed)
        assertEquals("missing rootfs command: curl", failed.summary)
    }

    @Test
    fun bootstrapRegistration_timeoutIsFailure() {
        val timedOut = ToolchainPackInstaller.resolveBootstrapResourceRegistration(
            resourceId = "kite.nodejs",
            version = "26.4.0",
            exitCode = -1,
            timedOut = true,
            summary = "exitCode=-1 timedOut=true"
        )

        assertFalse(timedOut.installed)
    }

    @Test
    fun bootstrapSummaryLine_prefersFailOverEndMarker() {
        val summary = ToolchainPackInstaller.bootstrapSummaryLine(
            """
            KFSHELL_AI_DEV_PACK_BEGIN
            FAIL	python-package	missing bundled package
            SUMMARY PASS=0 WARN=0 FAIL=1
            KFSHELL_AI_DEV_PACK_END
            """.trimIndent()
        )

        assertEquals("FAIL\tpython-package\tmissing bundled package", summary)
    }

    @Test
    fun bootstrapSummaryLine_keepsTransactionFailureReason() {
        val summary = ToolchainPackInstaller.bootstrapSummaryLine(
            "KITE_RESOURCE_TRANSACTION_FAILED stage=execute reason=Source file wasn't copied completely"
        )

        assertEquals(
            "KITE_RESOURCE_TRANSACTION_FAILED stage=execute reason=Source file wasn't copied completely",
            summary,
        )
    }

    @Test
    fun bootstrapResourceStatusSettled_acceptsInstalledAndFailedOnly() {
        assertTrue(ToolchainPackInstaller.bootstrapResourceStatusSettled("installed"))
        assertTrue(ToolchainPackInstaller.bootstrapResourceStatusSettled("failed"))
        assertFalse(ToolchainPackInstaller.bootstrapResourceStatusSettled(""))
        assertFalse(ToolchainPackInstaller.bootstrapResourceStatusSettled("installing"))
    }

    @Test
    fun bootstrapSettlementWaitsOnlyForActivePrepareRun() {
        assertTrue(
            ToolchainPackInstaller.shouldAwaitBootstrapSettlement(
                ToolchainInstallState(phase = ToolchainInstallPhase.RUNNING, action = "prepare")
            )
        )
        assertFalse(
            ToolchainPackInstaller.shouldAwaitBootstrapSettlement(
                ToolchainInstallState(phase = ToolchainInstallPhase.RUNNING, action = "doctor")
            )
        )
        assertFalse(
            ToolchainPackInstaller.shouldAwaitBootstrapSettlement(
                ToolchainInstallState(phase = ToolchainInstallPhase.SUCCEEDED, action = "prepare")
            )
        )
    }

    @Test
    fun bootstrapPhase_failsWhenAnyResourceFailed() {
        assertEquals(ToolchainInstallPhase.SUCCEEDED, ToolchainPackInstaller.resolveBootstrapPhase(0))
        assertEquals(ToolchainInstallPhase.FAILED, ToolchainPackInstaller.resolveBootstrapPhase(1))
    }

    @Test
    fun bootstrapInstall_missingComponentInstallsDirectlyAndRegisters() {
        val port = RecordingPort()
        val result = ToolchainPackInstaller.BootstrapInstallRunner(port).run(
            context = context,
            resourceId = "kite.nodejs",
            targetVersion = "26.4.0",
            runId = "bootstrap:kite.nodejs",
            alreadyReady = false
        ) {
            port.events += "execute"
            ToolchainPackInstaller.ToolchainCommandResult(0, false, 10L, "SUMMARY PASS=1 WARN=0 FAIL=0")
        }

        assertEquals(0, result.exitCode)
        assertEquals(
            listOf("status", "version", "installing", "execute", "installed"),
            port.events
        )
    }

    @Test
    fun bootstrapInstall_failureRegistersFailureWithoutViewRollback() {
        val port = RecordingPort()
        val result = ToolchainPackInstaller.BootstrapInstallRunner(port).run(
            context = context,
            resourceId = "kite.nodejs",
            targetVersion = "26.4.0",
            runId = "bootstrap:kite.nodejs",
            alreadyReady = false
        ) {
            port.events += "execute"
            ToolchainPackInstaller.ToolchainCommandResult(2, false, 10L, "FAIL\tnode-install\tbroken")
        }

        assertEquals(2, result.exitCode)
        assertEquals(
            listOf("status", "version", "installing", "execute", "failed"),
            port.events
        )
        assertFalse(port.events.contains("installed"))
    }

    @Test
    fun bootstrapInstall_readyComponentSkipsInstallAndRepairsRegistration() {
        val port = RecordingPort(status = "failed", version = "")
        val result = ToolchainPackInstaller.BootstrapInstallRunner(port).run(
            context = context,
            resourceId = "kite.nodejs",
            targetVersion = "26.4.0",
            runId = "bootstrap:kite.nodejs",
            alreadyReady = true
        ) {
            port.events += "execute"
            ToolchainPackInstaller.ToolchainCommandResult(0, false, 10L, "unexpected")
        }

        assertEquals(0, result.exitCode)
        assertEquals(listOf("status", "version", "installed"), port.events)
    }

    @Test
    fun bootstrapInstall_readyRegisteredComponentIsReadOnly() {
        val port = RecordingPort(status = "installed", version = "26.4.0")
        val result = ToolchainPackInstaller.BootstrapInstallRunner(port).run(
            context = context,
            resourceId = "kite.nodejs",
            targetVersion = "26.4.0",
            runId = "bootstrap:kite.nodejs",
            alreadyReady = true
        ) {
            port.events += "execute"
            ToolchainPackInstaller.ToolchainCommandResult(0, false, 10L, "unexpected")
        }

        assertEquals(0, result.exitCode)
        assertEquals(listOf("status", "version"), port.events)
    }

    @Test
    fun bootstrapSchedulingContractKeepsSixResourcesAndFormalDependency() {
        val contracts = ToolchainPackInstaller.bootstrapResourceSchedulingContracts()

        assertEquals(
            listOf(
                "kite.nodejs",
                "kite.python",
                "kite.uv",
                "kite.git",
                "kite.curl",
                "kite.tool.env",
            ),
            contracts.map { it.resourceId },
        )
        assertEquals(
            listOf(
                "--install-node",
                "--install-python",
                "--install-uv",
                "--install-git",
                "--install-curl",
                "--install-system-tools",
            ),
            contracts.map { it.mode },
        )
        assertTrue(contracts.take(5).all { it.dependencies.isEmpty() })
        assertEquals(setOf("kite.nodejs"), contracts.last().dependencies)
    }

    @Test
    fun bootstrapDependencyBlockedWritesExistingFailureStore() {
        val port = RecordingPort()
        val result = ToolchainPackInstaller.BootstrapInstallRunner(port).failWithoutExecution(
            context = context,
            resourceId = "kite.tool.env",
            runId = "bootstrap:kite.tool.env",
            reason = "dependency_failed:kite.nodejs",
        )

        assertEquals(2, result.exitCode)
        assertFalse(result.timedOut)
        assertTrue(result.output.contains("FAIL=1"))
        assertEquals(listOf("failed"), port.events)
    }

    private class RecordingPort(
        private val status: String = "",
        private val version: String = "1.0.0"
    ) : ToolchainResourcePort {
        val events = mutableListOf<String>()

        override fun currentEnvironmentId(context: Context): String = "default"
        override fun statusOf(context: Context, resourceId: String, environmentId: String): String =
            status.also { events += "status" }
        override fun versionOf(context: Context, resourceId: String, environmentId: String): String =
            version.also { events += "version" }
        override fun markInstalling(
            context: Context,
            resourceId: String,
            runId: String?,
            environmentId: String
        ) {
            events += "installing"
        }
        override fun markInstalled(
            context: Context,
            resourceId: String,
            version: String?,
            runId: String?,
            summary: String?,
            environmentId: String
        ) {
            events += "installed"
        }
        override fun markFailed(
            context: Context,
            resourceId: String,
            runId: String?,
            reason: String?,
            environmentId: String
        ) {
            events += "failed"
        }
    }
}
