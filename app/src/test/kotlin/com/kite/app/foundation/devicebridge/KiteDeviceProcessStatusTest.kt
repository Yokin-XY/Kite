package com.kite.app.foundation.devicebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KiteDeviceProcessStatusTest {
    @Test
    fun `status protocol round trips exit code`() {
        val source = KiteDeviceProcessStatus.completed(17)

        assertEquals(source, KiteDeviceProcessStatus.parse(source.encode()))
    }

    @Test
    fun `cancelled status uses stable shell exit semantics`() {
        val cancelled = KiteDeviceProcessStatus.cancelled()

        assertEquals(DeviceBridgeContract.EXIT_CANCELLED, cancelled.exitCode)
        assertEquals(cancelled, KiteDeviceProcessStatus.parse(cancelled.encode()))
    }

    @Test
    fun `unknown protocol version is rejected`() {
        assertNull(KiteDeviceProcessStatus.parse("v99 completed 0"))
    }
}
