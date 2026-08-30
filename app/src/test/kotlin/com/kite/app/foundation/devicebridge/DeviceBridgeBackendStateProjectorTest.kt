package com.kite.app.foundation.devicebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceBridgeBackendStateProjectorTest {
    @Test
    fun `Root 恢复时保持未检测且不会冒充正在连接`() {
        val snapshot = DeviceBridgeBackendStateProjector.rootNotChecked()

        assertEquals(DeviceBridgeBackendMode.RootExperimental, snapshot.selectedMode)
        assertEquals(DeviceBridgeLifecycleStatus.Unavailable, snapshot.lifecycle)
        assertEquals(DeviceBridgeBackendStateProjector.DETAIL_ROOT_NOT_CHECKED, snapshot.detail)
        assertFalse(snapshot.checking)
    }

    @Test
    fun `Root 探测结果统一投影身份和错误事实`() {
        val ready = DeviceBridgeBackendStateProjector.fromRootProbe(
            RootBridgeProbe(
                lifecycle = DeviceBridgeLifecycleStatus.Ready,
                identity = DeviceBridgeIdentity.Root,
                uid = 0,
                exitCode = DeviceBridgeContract.EXIT_OK,
                detail = "uid=0",
            )
        )

        assertEquals(DeviceBridgeLifecycleStatus.Ready, ready.lifecycle)
        assertEquals(DeviceBridgeIdentity.Root, ready.identity)
        assertEquals(0, ready.uid)
        assertFalse(ready.checking)
        assertTrue(DeviceBridgeBackendStateProjector.rootChecking().checking)
    }
}
