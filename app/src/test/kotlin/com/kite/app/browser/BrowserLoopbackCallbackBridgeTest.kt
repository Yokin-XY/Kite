package com.kite.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class BrowserLoopbackCallbackBridgeTest {
    @Test
    fun relaysIpv6LocalhostCallbackToIpv4InitiatorOnTheSamePort() {
        val targetServer = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            soTimeout = 5_000
        }
        val port = targetServer.localPort
        val targetFailure = AtomicReference<Throwable?>()
        val targetThread = thread(name = "loopback-target") {
            runCatching {
                targetServer.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
                    val requestLine = reader.readLine()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    assertTrue(requestLine.startsWith("GET /auth/callback?"))
                    val body = "ok".toByteArray(StandardCharsets.US_ASCII)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\n".toByteArray(StandardCharsets.US_ASCII))
                        write("Content-Length: ${body.size}\r\n".toByteArray(StandardCharsets.US_ASCII))
                        write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                        write(body)
                        flush()
                    }
                }
            }.onFailure(targetFailure::set)
        }
        val events = CopyOnWriteArrayList<BrowserLoopbackBridgeEvent>()
        val forwarded = CountDownLatch(1)
        val bridge = BrowserLoopbackCallbackBridge(
            eventSink = { event ->
                events.add(event)
                if (event.type == BrowserLoopbackBridgeEventType.CallbackForwarded) forwarded.countDown()
            }
        )
        val now = System.currentTimeMillis()
        val session = BrowserAuthSession(
            sessionId = "session",
            kind = BrowserAuthSessionKind.CliLoopback,
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "terminal_step",
            originalUrl = "https://login.example.test/authorize?redacted=true",
            requestKey = "request-key",
            redirectUri = "http://localhost:$port/auth/callback",
            state = "present",
            stateKey = "state-key",
            createdAt = now,
            expiresAt = now + 10_000,
            status = BrowserAuthSessionStatus.Pending
        )

        try {
            val preparation = bridge.prepare(session)
            assumeTrue(preparation.mode == BrowserLoopbackBridgeMode.RelayIpv6ToIpv4)

            Socket().use { browser ->
                browser.soTimeout = 5_000
                browser.connect(InetSocketAddress(InetAddress.getByName("::1"), port), 5_000)
                browser.getOutputStream().apply {
                    write(
                        "GET /auth/callback?code=secret-code&state=secret-state HTTP/1.1\r\n".toByteArray(
                            StandardCharsets.US_ASCII
                        )
                    )
                    write("Host: localhost:$port\r\n".toByteArray(StandardCharsets.US_ASCII))
                    write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                    flush()
                }
                val response = browser.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1).readText()
                assertTrue(response.startsWith("HTTP/1.1 200 OK"))
                assertTrue(response.endsWith("ok"))
            }

            assertTrue(forwarded.await(2, TimeUnit.SECONDS))
            assertEquals(1, events.count { it.type == BrowserLoopbackBridgeEventType.CallbackForwarded })
            assertTrue(events.none { it.reason.orEmpty().contains("secret") })
            targetFailure.get()?.let { throw AssertionError("target server failed", it) }
        } finally {
            bridge.stop(session.sessionId)
            runCatching { targetServer.close() }
            targetThread.join(5_000)
        }
    }

    @Test
    fun closesIdleBrowserPreconnectWithoutReportingRelayFailure() {
        val targetServer = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val port = targetServer.localPort
        val events = CopyOnWriteArrayList<BrowserLoopbackBridgeEvent>()
        val bridge = BrowserLoopbackCallbackBridge(
            eventSink = events::add,
            preRequestHeaderTimeoutMs = 150
        )
        val now = System.currentTimeMillis()
        val session = BrowserAuthSession(
            sessionId = "preconnect-session",
            kind = BrowserAuthSessionKind.CliLoopback,
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "terminal_step",
            originalUrl = "https://login.example.test/authorize?redacted=true",
            requestKey = "request-key",
            redirectUri = "http://localhost:$port/auth/callback",
            state = "present",
            stateKey = "state-key",
            createdAt = now,
            expiresAt = now + 10_000,
            status = BrowserAuthSessionStatus.Pending
        )

        try {
            val preparation = bridge.prepare(session)
            assumeTrue(preparation.mode == BrowserLoopbackBridgeMode.RelayIpv6ToIpv4)

            Socket().use { browser ->
                browser.soTimeout = 2_000
                browser.connect(InetSocketAddress(InetAddress.getByName("::1"), port), 2_000)
                val result = runCatching { browser.getInputStream().read() }.getOrDefault(-1)
                assertEquals(-1, result)
            }

            assertFalse(events.any { it.type == BrowserLoopbackBridgeEventType.RelayFailed })
            assertFalse(events.any { it.type == BrowserLoopbackBridgeEventType.CallbackForwarded })
        } finally {
            bridge.stop(session.sessionId)
            runCatching { targetServer.close() }
        }
    }
}
