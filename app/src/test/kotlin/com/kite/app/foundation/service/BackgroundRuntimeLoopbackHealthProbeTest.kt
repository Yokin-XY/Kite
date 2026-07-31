package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.RuntimeExposureScope
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRuntimeLoopbackHealthProbeTest {

    @Test
    fun `只为声明 loopback HTTP 能力的运行项建立端点`() {
        val record = record(healthHttpPath = "/readyz")

        assertEquals(
            BackgroundRuntimeLoopbackHealthEndpoint("127.0.0.1", 18789, "/readyz"),
            BackgroundRuntimeLoopbackHealthProbeResolver.resolve(record),
        )
        assertNull(BackgroundRuntimeLoopbackHealthProbeResolver.resolve(record.copy(bindAddress = "0.0.0.0")))
        assertNull(BackgroundRuntimeLoopbackHealthProbeResolver.resolve(record.copy(healthHttpPath = "https://x")))
        assertNull(BackgroundRuntimeLoopbackHealthProbeResolver.resolve(record.copy(healthHttpPath = "/ok\r\nX: y")))
        assertNull(BackgroundRuntimeLoopbackHealthProbeResolver.resolve(record.copy(healthHttpPath = "/not ready")))
    }

    @Test
    fun `HTTP 200 为健康且不需要启动容器命令`() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val responder = thread(name = "loopback-health-200") {
            server.use { socket ->
                socket.accept().use { client ->
                    client.getInputStream().bufferedReader().readLine()
                    client.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK"
                            .toByteArray(Charsets.US_ASCII)
                    )
                }
            }
        }

        val result = BackgroundRuntimeLoopbackHealthProbe.probe(
            BackgroundRuntimeLoopbackHealthEndpoint("127.0.0.1", server.localPort, "/readyz")
        )
        responder.join(2_000)

        assertTrue(result.healthy)
        assertEquals("loopback HTTP 200 /readyz", result.summary)
    }

    @Test
    fun `非 2xx 和连接拒绝都明确为不健康`() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val responder = thread(name = "loopback-health-503") {
            server.use { socket ->
                socket.accept().use { client ->
                    client.getInputStream().bufferedReader().readLine()
                    client.getOutputStream().write(
                        "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .toByteArray(Charsets.US_ASCII)
                    )
                }
            }
        }
        val unavailable = BackgroundRuntimeLoopbackHealthProbe.probe(
            BackgroundRuntimeLoopbackHealthEndpoint("127.0.0.1", port, "/readyz")
        )
        responder.join(2_000)

        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val refused = BackgroundRuntimeLoopbackHealthProbe.probe(
            BackgroundRuntimeLoopbackHealthEndpoint("127.0.0.1", closedPort, "/readyz")
        )

        assertFalse(unavailable.healthy)
        assertEquals("loopback HTTP 503 /readyz", unavailable.summary)
        assertFalse(refused.healthy)
        assertTrue(refused.summary.startsWith("loopback 连接失败："))
    }

    private fun record(healthHttpPath: String?): BackgroundRuntimeRecord = BackgroundRuntimeRecord(
        id = "runtime-test",
        spaceId = "space-main",
        kind = BackgroundRuntimeKind.CUSTOM,
        mode = BackgroundRuntimeMode.PROCESS,
        title = "test",
        workingDirectory = "/workspace",
        startCommand = "test-service",
        bindAddress = "127.0.0.1",
        bindPort = 18789,
        exposureScope = RuntimeExposureScope.LOOPBACK_ONLY,
        healthCommand = "expensive-cli health",
        healthHttpPath = healthHttpPath,
        logPath = "/tmp/runtime.log",
        createdAt = 1L,
    )
}
