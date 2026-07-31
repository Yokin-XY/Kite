package com.kite.app.foundation.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRuntimeStrongIdentityWiringContractTest {
    @Test
    fun `process creation captures identity and external recovery uses the shared policy`() {
        val hostSource = File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt"
        ).readText()
        val inspectorSource = File(
            "src/main/kotlin/com/kite/app/foundation/runtime/HostProcessSnapshot.kt"
        ).readText()

        assertTrue(
            hostSource.contains("captureRuntimeProcessIdentity(appContext, record.id, launchedPid)")
        )
        assertTrue(hostSource.contains("BackgroundRuntimeProcessIdentityPolicy.decide("))
        assertTrue(hostSource.contains("BackgroundRuntimeRecoveryAction.ATTACH_EXACT_PROCESS"))
        assertTrue(hostSource.contains("selectRefreshedBackgroundRuntimePid("))
        assertFalse(hostSource.contains("externalAlive -> externalPid ?: record.pid"))

        val narrowRead = inspectorSource.substringAfter("fun readAppProcessIdentity(")
            .substringBefore("private fun readProcSnapshot()")
        assertTrue(narrowRead.contains("File(\"/proc/\$pid\")"))
        assertTrue(narrowRead.contains("AndroidProcess.myUid().toString()"))
        assertFalse(narrowRead.contains("readSnapshot("))
        assertFalse(narrowRead.contains("listFiles"))
    }

    @Test
    fun `stop intent is persisted before guarded signalling and terminal state is conditional`() {
        val source = File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt"
        ).readText()
        val stopBody = source.substring(
            source.indexOf("private fun stopProcessRuntime"),
            source.indexOf("private fun confirmProcessRuntimeStopped"),
        )

        val expectedStopAt = stopBody.indexOf("RuntimeProcessStopReconciliation.markExpectedStop(")
        val inspectAt = stopBody.indexOf("inspectRuntimeHostProcess(")
        val guardedSignalAt = stopBody.indexOf("terminateExactHostProcess(")
        val conditionalConfirmAt = stopBody.indexOf("if (stopConfirmed)")
        val confirmAt = stopBody.indexOf("confirmProcessRuntimeStopped(")

        assertTrue(expectedStopAt >= 0)
        assertTrue(inspectAt > expectedStopAt)
        assertTrue(guardedSignalAt > inspectAt)
        assertTrue(conditionalConfirmAt > guardedSignalAt)
        assertTrue(confirmAt > conditionalConfirmAt)
        assertFalse(stopBody.contains("HostProcessTerminator.terminateHostProcess("))
        assertFalse(stopBody.contains("status = BackgroundRuntimeStatus.STOPPED"))
    }

    @Test
    fun `generic stale reconciliation delegates strong records to the identity aware host`() {
        val source = File(
            "src/main/kotlin/com/kite/app/foundation/runtime/RuntimeStateReconciler.kt"
        ).readText()
        val body = source.substring(
            source.indexOf("private fun reconcileBackgroundRuntime"),
            source.indexOf("private fun reconcileLegacyContainerPid"),
        )

        val strongIdentityAt = body.indexOf("current.persistedProcessIdentityOrNull() != null")
        val delegatedRefreshAt = body.indexOf("BackgroundRuntimeHost.refreshRuntimeStatuses(")
        val terminalWriteAt = body.indexOf("status = nextStatus")
        assertTrue(strongIdentityAt >= 0)
        assertTrue(delegatedRefreshAt > strongIdentityAt)
        assertTrue(terminalWriteAt > delegatedRefreshAt)
    }
}
