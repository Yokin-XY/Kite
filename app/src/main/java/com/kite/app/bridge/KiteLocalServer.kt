package com.kite.app.bridge

import com.kite.app.diagnostics.KiteDiagnostics
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import kotlin.concurrent.thread

class KiteLocalServer(
    private val diagnostics: KiteDiagnostics,
    private val openWeb: (String) -> Unit
) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    fun start(port: Int = DEFAULT_PORT) {
        if (running) return
        running = true
        thread(name = "KiteLocalServer", isDaemon = true) {
            runCatching {
                ServerSocket(port, 16, InetAddress.getByName("127.0.0.1")).use { socket ->
                    serverSocket = socket
                    while (running) {
                        val client = runCatching { socket.accept() }.getOrNull() ?: continue
                        thread(name = "KiteLocalServerClient", isDaemon = true) {
                            handleClient(client)
                        }
                    }
                }
            }.onFailure {
                diagnostics.logExternalUrl("local_server_error:${it.message}")
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 3000
            val request = readHttpRequest(client) ?: run {
                writeJson(client, 400, JSONObject().put("ok", false).put("error", "bad_request"))
                return
            }

            val parts = request.requestLine.split(" ")
            if (parts.size < 2) {
                writeJson(client, 400, JSONObject().put("ok", false).put("error", "bad_request"))
                return
            }

            val method = parts[0].uppercase(Locale.US)
            val path = parts[1].substringBefore("?")
            diagnostics.logLocalServer("$method $path")

            when {
                method == "GET" && path == "/status" -> writeJson(
                    client,
                    200,
                    JSONObject()
                        .put("ok", true)
                        .put("app", "Kite")
                        .put("version", "0.3")
                        .put("server", "running")
                )

                method == "GET" && path == "/capabilities" -> writeRawJson(
                    client,
                    200,
                    diagnostics.capabilitiesJson()
                )

                method == "POST" && path == "/open-web" -> {
                    val url = runCatching { JSONObject(request.body).getString("url") }.getOrNull()
                    if (url.isNullOrBlank()) {
                        writeJson(client, 400, JSONObject().put("ok", false).put("error", "missing_url"))
                    } else {
                        openWeb(url)
                        writeJson(
                            client,
                            200,
                            JSONObject()
                                .put("ok", true)
                                .put("accepted", true)
                                .put("url", url)
                        )
                    }
                }

                else -> writeJson(client, 404, JSONObject().put("ok", false).put("error", "not_found"))
            }
        }
    }

    private fun readHttpRequest(socket: Socket): HttpRequest? {
        val input = socket.getInputStream()
        val headerBytes = ArrayList<Byte>()
        while (true) {
            val next = input.read()
            if (next < 0) break
            headerBytes.add(next.toByte())
            val size = headerBytes.size
            if (size >= 4 &&
                headerBytes[size - 4] == '\r'.code.toByte() &&
                headerBytes[size - 3] == '\n'.code.toByte() &&
                headerBytes[size - 2] == '\r'.code.toByte() &&
                headerBytes[size - 1] == '\n'.code.toByte()
            ) {
                break
            }
            if (size > MAX_HEADER_BYTES) return null
        }
        if (headerBytes.isEmpty()) return null

        val headerText = headerBytes.toByteArray().toString(Charsets.UTF_8)
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val contentLength = lines
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        val bodyBytes = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(bodyBytes, offset, contentLength - offset)
            if (read < 0) break
            offset += read
        }
        return HttpRequest(requestLine, bodyBytes.copyOf(offset).toString(Charsets.UTF_8))
    }

    private fun writeJson(socket: Socket, status: Int, json: JSONObject) {
        writeRawJson(socket, status, json.toString())
    }

    private fun writeRawJson(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().write(
            "HTTP/1.1 $status ${statusText(status)}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.UTF_8)
        )
        socket.getOutputStream().write(bytes)
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        else -> "OK"
    }

    companion object {
        private const val DEFAULT_PORT = 8791
        private const val MAX_HEADER_BYTES = 16 * 1024
    }

    private data class HttpRequest(
        val requestLine: String,
        val body: String
    )
}
