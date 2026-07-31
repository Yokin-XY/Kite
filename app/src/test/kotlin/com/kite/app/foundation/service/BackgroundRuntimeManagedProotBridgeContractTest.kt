package com.kite.app.foundation.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRuntimeManagedProotBridgeContractTest {
    private val source = File(
        "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt"
    ).readText()

    @Test
    fun `proot capacity and starting checkpoint happen before the only process creation`() {
        val startBody = source.substringAfter("private fun startProcessRuntime(")
            .substringBefore("private fun buildRuntimeProcessLaunchConfig(")
        val configAt = startBody.indexOf("buildRuntimeProcessLaunchConfig(appContext, record)")
        val admissionAt = startBody.indexOf("acquireAndPersistManagedProotLease(")
        val processAt = startBody.indexOf("ProcessBuilder(config.command)")

        assertTrue(configAt >= 0)
        assertTrue(admissionAt > configAt)
        assertTrue(processAt > admissionAt)
        assertTrue(startBody.contains("startingProotGeneration"))
        assertTrue(startBody.contains("releaseManagedProotLeaseAfterStartFailure"))
    }

    @Test
    fun `strong identity precedes running phase and stop settlement precedes release`() {
        val startBody = source.substringAfter("private fun startProcessRuntime(")
            .substringBefore("private fun buildRuntimeProcessLaunchConfig(")
        val identityAt = startBody.indexOf("captureRuntimeProcessIdentity")
        val runningAt = startBody.indexOf("markManagedProotLeaseRunningIfIdentityReady")
        assertTrue(identityAt >= 0)
        assertTrue(runningAt > identityAt)

        val stopBody = source.substringAfter("private fun stopProcessRuntime(")
            .substringBefore("private fun confirmProcessRuntimeStopped(")
        val stoppingAt = stopBody.indexOf("beginManagedProotLeaseStop")
        val ownerStopAt = stopBody.indexOf("ProotOwnerProcessTerminator.terminate")
        val settledAt = stopBody.indexOf("ownerTermination?.settled == true")
        val confirmAt = stopBody.indexOf("confirmProcessRuntimeStopped")
        assertTrue(stoppingAt >= 0)
        assertTrue(ownerStopAt > stoppingAt)
        assertTrue(settledAt > ownerStopAt)
        assertTrue(confirmAt > settledAt)

        val confirmBody = source.substringAfter("private fun confirmProcessRuntimeStopped(")
            .substringBefore("private fun refreshProcessRuntimeStatus(")
        assertTrue(confirmBody.contains("releaseManagedProotLeaseAfterConfirmedStop"))
    }

    @Test
    fun `terminal runtime still enters stop path while a managed proot lease is unreleased`() {
        val stopEntry = source.substringAfter("private suspend fun stopRuntimeInternal(")
            .substringBefore("private fun ensureRuntimePrerequisites(")

        assertTrue(
            stopEntry.contains(
                "record.status.isTerminalStatus() && !record.hasUnreleasedLongLivedProotLease()"
            )
        )
        assertTrue(stopEntry.contains("BackgroundRuntimeMode.PROCESS -> stopProcessRuntime"))
    }

    @Test
    fun `all compatibility fallbacks use the generic proot lane without product special cases`() {
        val launchBody = source.substringAfter("private fun buildRuntimeProcessLaunchConfig(")
            .substringBefore("private fun resolveRuntimeEnvironment(")

        assertFalse(launchBody.contains("proot_fallback"))
        assertTrue(launchBody.windowed("route = \"proot_shell\"".length)
            .count { it == "route = \"proot_shell\"" } >= 2)
        assertFalse(source.contains("kite.openclaw"))
        assertFalse(source.contains("LongLivedProotAdmissionSimulator"))
    }
}
