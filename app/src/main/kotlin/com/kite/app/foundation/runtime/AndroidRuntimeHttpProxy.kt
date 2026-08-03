package com.kite.app.foundation.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.kite.app.foundation.logging.Logger
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * 把 Linux/glibc 运行时的网络请求交回 Android 当前默认网络。
 *
 * glibc 无法使用 Android netd 的按网络 DNS；本地代理改用 Android [Network] 解析域名，
 * 同时保持 Agent 只看到稳定的 loopback 代理地址。默认网络切换时关闭旧隧道，客户端会按
 * 原请求语义重连，从而同时覆盖先开 Kite、后开 VPN 和 VPN 运行中切换的场景。
 */
internal object AndroidRuntimeHttpProxy {
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val REQUEST_HEADER_TIMEOUT_MS = 10_000
    private const val LOOPBACK_HOST = "127.0.0.1"
    private const val NO_PROXY_HOSTS = "localhost,127.0.0.1,::1"

    private val startLock = Any()
    private val tunnels = ConcurrentHashMap.newKeySet<Tunnel>()
    private val clients = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "KiteRuntimeProxyClient").apply { isDaemon = true }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var activeNetwork: Network? = null

    fun ensureStarted(context: Context): Boolean {
        if (serverSocket?.isClosed == false) return true
        synchronized(startLock) {
            if (serverSocket?.isClosed == false) return true
            val appContext = context.applicationContext
            activeNetwork = (
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                )?.activeNetwork
            return runCatching {
                ServerSocket(0, 32, InetAddress.getByName(LOOPBACK_HOST)).also { server ->
                    serverSocket = server
                    thread(name = "KiteRuntimeProxy", isDaemon = true) { acceptClients(server) }
                    Logger.i("ContainerNetwork", "Android 运行时网络代理已启动: port=${server.localPort}")
                }
            }.onFailure { error ->
                serverSocket = null
                Logger.e("ContainerNetwork", "启动 Android 运行时网络代理失败: ${error.message}")
            }.isSuccess
        }
    }

    fun environment(): Map<String, String> {
        val port = serverSocket?.localPort?.takeIf { it > 0 } ?: return emptyMap()
        val proxyUrl = "http://$LOOPBACK_HOST:$port"
        return linkedMapOf(
            "HTTP_PROXY" to proxyUrl,
            "HTTPS_PROXY" to proxyUrl,
            "http_proxy" to proxyUrl,
            "https_proxy" to proxyUrl,
            "NO_PROXY" to NO_PROXY_HOSTS,
            "no_proxy" to NO_PROXY_HOSTS,
            // Node 24+ 的 fetch/HTTP 客户端只有打开此开关才读取代理环境变量。
            "NODE_USE_ENV_PROXY" to "1",
        )
    }

    fun updateDefaultNetwork(network: Network?, reason: String) {
        val previous = activeNetwork
        activeNetwork = network
        if (previous == network) return
        val closed = tunnels.toList().count { tunnel ->
            tunnel.close()
            true
        }
        Logger.i(
            "ContainerNetwork",
            "Android 默认网络已切换，运行时代理关闭旧隧道: reason=$reason closed=$closed",
        )
    }

    private fun acceptClients(server: ServerSocket) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: continue
            clients.execute {
                runCatching { handleClient(client) }
                    .onFailure { error ->
                        Logger.e("ContainerNetwork", "运行时代理请求失败: ${error.message}")
                    }
                closeQuietly(client)
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.soTimeout = REQUEST_HEADER_TIMEOUT_MS
        val requestHead = readRequestHead(client) ?: return
        val request = RuntimeHttpProxyRequest.parse(requestHead) ?: run {
            client.getOutputStream().write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        val network = activeNetwork
        val upstream = connect(network, request.host, request.port)
        // Agent 的思考阶段可能长时间没有响应字节；建立隧道后不能用读超时截断流式会话。
        client.soTimeout = 0
        upstream.soTimeout = 0
        val tunnel = Tunnel(client, upstream)
        tunnels += tunnel
        try {
            if (request.isConnect) {
                client.getOutputStream().apply {
                    write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    flush()
                }
            } else {
                upstream.getOutputStream().apply {
                    write(checkNotNull(request.forwardedHead))
                    flush()
                }
            }
            relay(tunnel)
        } finally {
            tunnels -= tunnel
            tunnel.close()
        }
    }

    private fun connect(network: Network?, host: String, port: Int): Socket {
        val addresses = if (network != null) {
            network.getAllByName(host).toList()
        } else {
            InetAddress.getAllByName(host).toList()
        }
        var lastFailure: Throwable? = null
        for (address in addresses) {
            val socket = if (network != null) network.socketFactory.createSocket() else Socket()
            try {
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                return socket
            } catch (error: Throwable) {
                lastFailure = error
                closeQuietly(socket)
            }
        }
        throw lastFailure ?: IllegalStateException("无法解析代理目标: $host")
    }

    private fun readRequestHead(client: Socket): ByteArray? {
        val input = client.getInputStream()
        val output = ByteArrayOutputStream(1024)
        var tail = 0
        while (output.size() < MAX_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) return null
            output.write(value)
            tail = ((tail shl 8) or value) and 0xffffffff.toInt()
            if (tail == 0x0d0a0d0a || (tail and 0x0000ffff) == 0x00000a0a) {
                return output.toByteArray()
            }
        }
        throw IllegalArgumentException("代理请求头超过限制")
    }

    private fun relay(tunnel: Tunnel) {
        val reverse = thread(name = "KiteRuntimeProxyResponse", isDaemon = true) {
            runCatching {
                tunnel.upstream.getInputStream().copyTo(tunnel.client.getOutputStream())
                tunnel.client.shutdownOutput()
            }
        }
        runCatching {
            tunnel.client.getInputStream().copyTo(tunnel.upstream.getOutputStream())
            tunnel.upstream.shutdownOutput()
        }
        reverse.join()
    }

    private data class Tunnel(val client: Socket, val upstream: Socket) : Closeable {
        override fun close() {
            closeQuietly(client)
            closeQuietly(upstream)
        }
    }

    private fun closeQuietly(closeable: Closeable) {
        runCatching { closeable.close() }
    }
}
