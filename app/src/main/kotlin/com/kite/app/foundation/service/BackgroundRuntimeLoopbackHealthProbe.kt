package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.RuntimeExposureScope
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

internal data class BackgroundRuntimeLoopbackHealthEndpoint(
    val host: String,
    val port: Int,
    val path: String,
)

internal data class BackgroundRuntimeLoopbackHealthResult(
    val healthy: Boolean,
    val summary: String,
)

internal object BackgroundRuntimeLoopbackHealthProbeResolver {
    private val allowedHosts = setOf("127.0.0.1", "localhost", "::1")

    fun resolve(record: BackgroundRuntimeRecord): BackgroundRuntimeLoopbackHealthEndpoint? {
        if (record.exposureScope != RuntimeExposureScope.LOOPBACK_ONLY) return null
        val host = record.bindAddress?.trim()?.lowercase().orEmpty()
        if (host !in allowedHosts) return null
        val port = record.bindPort?.takeIf { it in 1..65535 } ?: return null
        val path = record.healthHttpPath?.trim().orEmpty()
        if (!isSafeHttpPath(path)) return null
        return BackgroundRuntimeLoopbackHealthEndpoint(host = host, port = port, path = path)
    }

    private fun isSafeHttpPath(path: String): Boolean =
        path.startsWith('/') &&
            !path.startsWith("//") &&
            path.all { character -> character.code in 0x21..0x7e } &&
            "://" !in path
}

internal object BackgroundRuntimeLoopbackHealthProbe {
    private const val CONNECT_TIMEOUT_MS = 750
    private const val READ_TIMEOUT_MS = 1_250
    private val statusLinePattern = Regex("^HTTP/\\d(?:\\.\\d)?\\s+(\\d{3})(?:\\s+.*)?$")

    fun probe(
        endpoint: BackgroundRuntimeLoopbackHealthEndpoint,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): BackgroundRuntimeLoopbackHealthResult = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs
            OutputStreamWriter(socket.getOutputStream(), Charsets.US_ASCII).use { writer ->
                val hostHeader = if (':' in endpoint.host) "[${endpoint.host}]" else endpoint.host
                writer.write("GET ${endpoint.path} HTTP/1.1\r\n")
                writer.write("Host: $hostHeader:${endpoint.port}\r\n")
                writer.write("Connection: close\r\n\r\n")
                writer.flush()

                val statusLine = BufferedReader(socket.getInputStream().reader(Charsets.US_ASCII)).readLine()
                    ?.trim()
                    .orEmpty()
                val statusCode = statusLinePattern.matchEntire(statusLine)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                if (statusCode == null) {
                    BackgroundRuntimeLoopbackHealthResult(
                        healthy = false,
                        summary = "loopback HTTP 响应无效",
                    )
                } else {
                    BackgroundRuntimeLoopbackHealthResult(
                        healthy = statusCode in 200..299,
                        summary = "loopback HTTP $statusCode ${endpoint.path}",
                    )
                }
            }
        }
    }.getOrElse { error ->
        BackgroundRuntimeLoopbackHealthResult(
            healthy = false,
            summary = "loopback 连接失败：${error.javaClass.simpleName}",
        )
    }
}
