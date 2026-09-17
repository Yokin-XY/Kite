package com.kite.app.foundation.devicebridge

import org.junit.Assert.assertEquals
import org.junit.Test

class RootBridgeProbeClassifierTest {
    @Test
    fun acceptsOnlyRealUidZeroAsReady() {
        val root = RootBridgeProbeClassifier.classify(0, "0\n", "")
        assertEquals(DeviceBridgeLifecycleStatus.Ready, root.lifecycle)
        assertEquals(DeviceBridgeIdentity.Root, root.identity)
        assertEquals(DeviceBridgeContract.EXIT_OK, root.exitCode)

        val shell = RootBridgeProbeClassifier.classify(0, "2000\n", "")
        assertEquals(DeviceBridgeLifecycleStatus.Failed, shell.lifecycle)
        assertEquals(DeviceBridgeContract.EXIT_BACKEND_UNAVAILABLE, shell.exitCode)
    }

    @Test
    fun distinguishesRejectionTimeoutAndMissingSu() {
        val denied = RootBridgeProbeClassifier.classify(1, "", "permission denied")
        assertEquals(DeviceBridgeLifecycleStatus.PermissionRequired, denied.lifecycle)
        assertEquals(DeviceBridgeContract.EXIT_PERMISSION_DENIED, denied.exitCode)

        val timeout = RootBridgeProbeClassifier.classify(null, "", "", timedOut = true)
        assertEquals(DeviceBridgeContract.EXIT_TIMEOUT, timeout.exitCode)

        val missing = RootBridgeProbeClassifier.classify(
            null,
            "",
            "",
            startFailure = java.io.IOException("missing")
        )
        assertEquals(DeviceBridgeLifecycleStatus.Unavailable, missing.lifecycle)
        assertEquals(DeviceBridgeContract.EXIT_BACKEND_UNAVAILABLE, missing.exitCode)
    }

    @Test
    fun unknownStoredBackendNeverEnablesRoot() {
        assertEquals(DeviceBridgeBackendMode.Shizuku, DeviceBridgeBackendMode.fromStorage(null))
        assertEquals(DeviceBridgeBackendMode.Shizuku, DeviceBridgeBackendMode.fromStorage("unknown"))
        assertEquals(
            DeviceBridgeBackendMode.RootExperimental,
            DeviceBridgeBackendMode.fromStorage("root_experimental")
        )
    }
}
