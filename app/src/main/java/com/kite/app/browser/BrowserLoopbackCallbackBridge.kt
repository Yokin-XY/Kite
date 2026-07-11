package com.kite.app.browser

import android.content.Context
import com.kite.app.foundation.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class BrowserLoopbackBridgeMode {
    Direct,
    RelayIpv6ToIpv4,
    RelayIpv4ToIpv6,
    Unavailable
}

data class BrowserLoopbackBridgePreparation(
    val mode: BrowserLoopbackBridgeMode,
    val port: Int?,
    val reason: String? = null
)

internal enum class BrowserLoopbackBridgeEventType {
    Direct,
    RelayReady,
    CallbackForwarded,
    RelayFailed
}

internal data class BrowserLoopbackBridgeEvent(
    val sessionId: String,
    val type: BrowserLoopbackBridgeEventType,
    val mode: BrowserLoopbackBridgeMode,
    val port: Int,
    val reason: String? = null
)

/**
 * Bridges only the Android localhost address-family gap. OAuth validation and credential storage
 * remain owned by the process that created the loopback callback listener.
 */
class BrowserLoopbackCallbackBridge internal constructor(
    private val eventSink: (BrowserLoopbackBridgeEvent) -> Unit,
    private val preRequestHeaderTimeoutMs: Int = PRE_REQUEST_HEADER_TIMEOUT_MS
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relays = ConcurrentHashMap<String, ActiveRelay>()

    fun prepare(session: BrowserAuthSession): BrowserLoopbackBridgePreparation {
        if (session.kind != BrowserAuthSessionKind.CliLoopback) {
            return BrowserLoopbackBridgePreparation(
                mode = BrowserLoopbackBridgeMode.Unavailable,
                port = null,
                reason = "not_cli_loopback"
            )
        }
        val endpoint = session.redirectUri
            ?.let(BrowserHandoffPolicy::loopbackCallbackEndpoint)
            ?: return unavailable(session.sessionId, "invalid_loopback_redirect")

        relays[session.sessionId]?.let { existing ->
            if (!existing.closed.get() && existing.endpoint == endpoint) {
                return existing.preparation()
            }
        }
        stopRelaysOnPort(endpoint.port)

        if (endpoint.hostKind == BrowserLoopbackHostKind.Ipv4) {
            return direct(session.sessionId, endpoint.port, "ipv4_loopback")
        }

        val ipv6Relay = startRelay(
            session = session,
            endpoint = endpoint,
            listenAddress = IPV6_LOOPBACK,
            targetAddress = IPV4_LOOPBACK,
            mode = BrowserLoopbackBridgeMode.RelayIpv6ToIpv4
        )
        if (ipv6Relay != null) return ipv6Relay.preparation()

        if (endpoint.hostKind == BrowserLoopbackHostKind.Localhost) {
            val ipv4Relay = startRelay(
                session = session,
                endpoint = endpoint,
                listenAddress = IPV4_LOOPBACK,
                targetAddress = IPV6_LOOPBACK,
                mode = BrowserLoopbackBridgeMode.RelayIpv4ToIpv6
            )
            if (ipv4Relay != null) return ipv4Relay.preparation()
        }

        return direct(session.sessionId, endpoint.port, "loopback_listener_already_available")
    }

    fun stop(sessionId: String) {
        relays.remove(sessionId)?.close()
    }

    private fun startRelay(
        session: BrowserAuthSession,
        endpoint: BrowserLoopbackCallbackEndpoint,
        listenAddress: String,
        targetAddress: String,
        mode: BrowserLoopbackBridgeMode
    ): ActiveRelay? {
        val server = bindServer(listenAddress, endpoint.port) ?: return null
        val relay = ActiveRelay(
            sessionId = session.sessionId,
            endpoint = endpoint,
            mode = mode,
            targetAddress = targetAddress,
            expiresAt = session.expiresAt,
            server = server
        )
        relays[session.sessionId] = relay
        emit(
            BrowserLoopbackBridgeEvent(
                sessionId = session.sessionId,
                type = BrowserLoopbackBridgeEventType.RelayReady,
                mode = mode,
                port = endpoint.port
            )
        )
        relay.acceptJob = scope.launch { acceptConnections(relay) }
        relay.expiryJob = scope.launch {
            delay((session.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
            stop(session.sessionId)
        }
        return relay
    }

    private suspend fun acceptConnections(relay: ActiveRelay) {
        try {
            while (scope.isActive && !relay.closed.get() && System.currentTimeMillis() < relay.expiresAt) {
                val client = try {
                    relay.server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                relay.openSockets.add(client)
                scope.launch { forwardConnection(relay, client) }
            }
        } catch (error: SocketException) {
            if (!relay.closed.get()) {
                relayFailed(relay, "accept_failed", error)
            }
        } catch (error: IOException) {
            relayFailed(relay, "accept_failed", error)
        } finally {
            relays.remove(relay.sessionId, relay)
            relay.close()
        }
    }

    private suspend fun forwardConnection(relay: ActiveRelay, client: Socket) {
        try {
            client.soTimeout = preRequestHeaderTimeoutMs
            val requestHeader = try {
                readHttpHeader(client)
            } catch (_: SocketTimeoutException) {
                return
            } ?: return
            client.soTimeout = CONNECTION_TIMEOUT_MS
            val target = connectTarget(relay.targetAddress, relay.endpoint.port)
            if (target == null) {
                writeGatewayFailure(client)
                relayFailed(relay, "target_unavailable", null)
                return
            }
            relay.openSockets.add(target)
            try {
                target.use {
                    target.soTimeout = CONNECTION_TIMEOUT_MS
                    target.getOutputStream().apply {
                        write(requestHeader)
                        flush()
                    }
                    if (matchesCallbackPath(requestHeader, relay.endpoint.path) &&
                        relay.callbackForwarded.compareAndSet(false, true)
                    ) {
                        emit(
                            BrowserLoopbackBridgeEvent(
                                sessionId = relay.sessionId,
                                type = BrowserLoopbackBridgeEventType.CallbackForwarded,
                                mode = relay.mode,
                                port = relay.endpoint.port
                            )
                        )
                    }
                    proxyRemainingTraffic(client, target)
                }
            } finally {
                relay.openSockets.remove(target)
            }
        } catch (error: IOException) {
            relayFailed(relay, "connection_failed", error)
        } finally {
            relay.openSockets.remove(client)
            runCatching { client.close() }
        }
    }

    private suspend fun proxyRemainingTraffic(client: Socket, target: Socket) = coroutineScope {
        val requestBodyJob = launch(Dispatchers.IO) {
            runCatching {
                client.getInputStream().copyTo(target.getOutputStream())
                target.getOutputStream().flush()
                target.shutdownOutput()
            }
        }
        try {
            target.getInputStream().copyTo(client.getOutputStream())
            client.getOutputStream().flush()
        } finally {
            runCatching { target.close() }
            runCatching { client.close() }
            requestBodyJob.cancelAndJoin()
        }
    }

    private suspend fun connectTarget(host: String, port: Int): Socket? {
        val deadline = System.currentTimeMillis() + TARGET_CONNECT_WINDOW_MS
        do {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(InetAddress.getByName(host), port), TARGET_CONNECT_ATTEMPT_MS)
                return socket
            } catch (_: IOException) {
                runCatching { socket.close() }
                delay(TARGET_CONNECT_RETRY_MS)
            }
        } while (System.currentTimeMillis() < deadline)
        return null
    }

    private fun readHttpHeader(socket: Socket): ByteArray? {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        var terminatorState = 0
        while (output.size() < MAX_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) return null
            output.write(value)
            terminatorState = when (terminatorState) {
                0 -> if (value == CR) 1 else 0
                1 -> when (value) {
                    LF -> 2
                    CR -> 1
                    else -> 0
                }
                2 -> if (value == CR) 3 else 0
                else -> if (value == LF) 4 else 0
            }
            if (terminatorState == 4) return output.toByteArray()
        }
        return null
    }

    private fun matchesCallbackPath(header: ByteArray, expectedPath: String): Boolean {
        val firstLine = String(header, StandardCharsets.ISO_8859_1).substringBefore("\r\n")
        val requestTarget = firstLine.split(" ", limit = 3).getOrNull(1) ?: return false
        val path = if (requestTarget.startsWith("http://", true) || requestTarget.startsWith("https://", true)) {
            runCatching { URI(requestTarget).rawPath }.getOrNull()
        } else {
            requestTarget.substringBefore("?").substringBefore("#")
        }
        return path?.ifBlank { "/" } == expectedPath
    }

    private fun bindServer(host: String, port: Int): ServerSocket? {
        val server = ServerSocket()
        return try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(InetAddress.getByName(host), port), LISTEN_BACKLOG)
            server.soTimeout = ACCEPT_POLL_MS
            server
        } catch (_: BindException) {
            runCatching { server.close() }
            null
        } catch (_: IOException) {
            runCatching { server.close() }
            null
        } catch (_: SecurityException) {
            runCatching { server.close() }
            null
        }
    }

    private fun writeGatewayFailure(client: Socket) {
        val body = "Kite 未能连接登录发起方，请返回应用重新发起登录。".toByteArray(Charsets.UTF_8)
        runCatching {
            client.getOutputStream().apply {
                write("HTTP/1.1 502 Bad Gateway\r\n".toByteArray(StandardCharsets.US_ASCII))
                write("Content-Type: text/plain; charset=utf-8\r\n".toByteArray(StandardCharsets.US_ASCII))
                write("Content-Length: ${body.size}\r\n".toByteArray(StandardCharsets.US_ASCII))
                write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                write(body)
                flush()
            }
        }
    }

    private fun stopRelaysOnPort(port: Int) {
        relays.values
            .filter { it.endpoint.port == port }
            .map { it.sessionId }
            .forEach(::stop)
    }

    private fun direct(sessionId: String, port: Int, reason: String): BrowserLoopbackBridgePreparation {
        emit(
            BrowserLoopbackBridgeEvent(
                sessionId = sessionId,
                type = BrowserLoopbackBridgeEventType.Direct,
                mode = BrowserLoopbackBridgeMode.Direct,
                port = port,
                reason = reason
            )
        )
        return BrowserLoopbackBridgePreparation(BrowserLoopbackBridgeMode.Direct, port, reason)
    }

    private fun unavailable(sessionId: String, reason: String): BrowserLoopbackBridgePreparation {
        emit(
            BrowserLoopbackBridgeEvent(
                sessionId = sessionId,
                type = BrowserLoopbackBridgeEventType.RelayFailed,
                mode = BrowserLoopbackBridgeMode.Unavailable,
                port = 0,
                reason = reason
            )
        )
        return BrowserLoopbackBridgePreparation(BrowserLoopbackBridgeMode.Unavailable, null, reason)
    }

    private fun relayFailed(relay: ActiveRelay, reason: String, error: Throwable?) {
        emit(
            BrowserLoopbackBridgeEvent(
                sessionId = relay.sessionId,
                type = BrowserLoopbackBridgeEventType.RelayFailed,
                mode = relay.mode,
                port = relay.endpoint.port,
                reason = reason
            )
        )
        if (error != null) {
            Logger.e(LOG_TAG, "loopback callback relay failed: reason=$reason error=${error.message}")
        }
    }

    private fun emit(event: BrowserLoopbackBridgeEvent) {
        runCatching { eventSink(event) }
    }

    private class ActiveRelay(
        val sessionId: String,
        val endpoint: BrowserLoopbackCallbackEndpoint,
        val mode: BrowserLoopbackBridgeMode,
        val targetAddress: String,
        val expiresAt: Long,
        val server: ServerSocket
    ) {
        val closed = AtomicBoolean(false)
        val callbackForwarded = AtomicBoolean(false)
        val openSockets = ConcurrentHashMap.newKeySet<Socket>()
        @Volatile var acceptJob: Job? = null
        @Volatile var expiryJob: Job? = null

        fun preparation(): BrowserLoopbackBridgePreparation =
            BrowserLoopbackBridgePreparation(mode = mode, port = endpoint.port)

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { server.close() }
            openSockets.forEach { socket -> runCatching { socket.close() } }
            openSockets.clear()
            acceptJob?.cancel()
            expiryJob?.cancel()
        }
    }

    companion object {
        private const val LOG_TAG = "BrowserLoopbackBridge"
        private const val IPV4_LOOPBACK = "127.0.0.1"
        private const val IPV6_LOOPBACK = "::1"
        private const val LISTEN_BACKLOG = 8
        private const val ACCEPT_POLL_MS = 500
        private const val PRE_REQUEST_HEADER_TIMEOUT_MS = 5_000
        private const val CONNECTION_TIMEOUT_MS = 120_000
        private const val TARGET_CONNECT_WINDOW_MS = 4_000L
        private const val TARGET_CONNECT_ATTEMPT_MS = 800
        private const val TARGET_CONNECT_RETRY_MS = 120L
        private const val MAX_HEADER_BYTES = 64 * 1024
        private const val CR = 13
        private const val LF = 10

        @Volatile
        private var instance: BrowserLoopbackCallbackBridge? = null

        fun get(context: Context): BrowserLoopbackCallbackBridge =
            instance ?: synchronized(this) {
                instance ?: BrowserLoopbackCallbackBridge(createEventSink(context.applicationContext))
                    .also { instance = it }
            }

        private fun createEventSink(context: Context): (BrowserLoopbackBridgeEvent) -> Unit {
            val store = BrowserAuthSessionStore(context)
            return { event ->
                when (event.type) {
                    BrowserLoopbackBridgeEventType.Direct -> store.markLoopbackCallbackChannel(
                        sessionId = event.sessionId,
                        status = BrowserAuthCallbackChannelStatus.Direct
                    )
                    BrowserLoopbackBridgeEventType.RelayReady -> store.markLoopbackCallbackChannel(
                        sessionId = event.sessionId,
                        status = BrowserAuthCallbackChannelStatus.RelayReady
                    )
                    BrowserLoopbackBridgeEventType.CallbackForwarded ->
                        store.markLoopbackCallbackForwarded(event.sessionId)
                    BrowserLoopbackBridgeEventType.RelayFailed -> store.markLoopbackCallbackChannel(
                        sessionId = event.sessionId,
                        status = BrowserAuthCallbackChannelStatus.RelayUnavailable,
                        reason = event.reason
                    )
                }
                Logger.i(
                    LOG_TAG,
                    "loopback callback channel: event=${event.type.name} mode=${event.mode.name} port=${event.port} reason=${event.reason.orEmpty()}"
                )
            }
        }
    }
}
