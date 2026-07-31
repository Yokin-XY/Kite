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
}
