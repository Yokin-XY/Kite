package com.kite.app.platform.runtimebootstrap

import com.kite.app.foundation.bootstrap.BootstrapStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRuntimeBootstrapGatewayTest {
    @Test
    fun readinessIsRecheckedOnlyAfterBootstrapReachesATerminalStage() {
        assertFalse(AndroidRuntimeBootstrapGateway.shouldRefreshReadiness(BootstrapStage.IDLE))
        assertFalse(AndroidRuntimeBootstrapGateway.shouldRefreshReadiness(BootstrapStage.ROOTFS_EXTRACTING))
        assertFalse(AndroidRuntimeBootstrapGateway.shouldRefreshReadiness(BootstrapStage.SPACE_READY))
        assertTrue(AndroidRuntimeBootstrapGateway.shouldRefreshReadiness(BootstrapStage.READY))
        assertTrue(AndroidRuntimeBootstrapGateway.shouldRefreshReadiness(BootstrapStage.FAILED))
    }
}
