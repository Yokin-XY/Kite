package com.kite.app.bridge

import java.net.SocketException
import org.junit.Assert.assertEquals
import org.junit.Test

class KiteLocalServerClientFailureTest {
    @Test
    fun brokenPipeIsClassifiedAsClientDisconnect() {
        assertEquals(
            "local_server_client_disconnected:SocketException",
            localServerClientFailureEvent(SocketException("Broken pipe"))
        )
    }

    @Test
    fun unexpectedFailureStaysVisibleWithoutEscapingClientBoundary() {
        assertEquals(
            "local_server_client_failed:IllegalStateException",
            localServerClientFailureEvent(IllegalStateException("bad response"))
        )
    }
}
