package com.kite.app.bridge

import android.content.Context
import com.kite.app.diagnostics.KiteDiagnostics
import com.kftest.app.foundation.toolchain.ToolchainPackInstaller
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import kotlin.concurrent.thread

class KiteLocalServer(
    context: Context,
    private val diagnostics: KiteDiagnostics,
    private val openWeb: (String) -> Unit
) {
    private val appContext = context.applicationContext

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

                method == "GET" && path == "/toolchain" -> writeHtml(
                    client,
                    200,
                    toolchainPageHtml()
                )

                method == "GET" && path == "/toolchain/status" -> {
                    ToolchainPackInstaller.refreshState(appContext)
                    writeJson(client, 200, ToolchainPackInstaller.state.value.toJson())
                }

                method == "POST" && path == "/toolchain/prepare" -> {
                    ToolchainPackInstaller.prepareAiEnv(appContext)
                    writeJson(client, 202, JSONObject().put("ok", true).put("accepted", true).put("action", "prepare_ai_env"))
                }

                method == "POST" && path == "/toolchain/doctor" -> {
                    ToolchainPackInstaller.doctor(appContext)
                    writeJson(client, 202, JSONObject().put("ok", true).put("accepted", true).put("action", "toolchain_doctor"))
                }

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

    private fun writeHtml(socket: Socket, status: Int, body: String) {
        writeRaw(socket, status, "text/html; charset=utf-8", body)
    }

    private fun writeRawJson(socket: Socket, status: Int, body: String) {
        writeRaw(socket, status, "application/json; charset=utf-8", body)
    }

    private fun writeRaw(socket: Socket, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().write(
            "HTTP/1.1 $status ${statusText(status)}\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
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

    private fun toolchainPageHtml(): String = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>KF AI Toolchain</title>
          <style>
            :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
            body { margin: 0; padding: 18px; background: #f6f7f9; color: #172033; }
            main { max-width: 760px; margin: 0 auto; }
            h1 { font-size: 22px; margin: 0 0 8px; }
            p { color: #5d667a; line-height: 1.45; }
            .panel { background: #fff; border: 1px solid #dfe3ea; border-radius: 8px; padding: 14px; margin-top: 12px; }
            .row { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
            button { border: 0; border-radius: 8px; padding: 11px 14px; background: #16856a; color: white; font-weight: 700; }
            button.secondary { background: #354052; }
            pre { white-space: pre-wrap; word-break: break-word; max-height: 46vh; overflow: auto; background: #111827; color: #e5e7eb; padding: 12px; border-radius: 8px; }
            .status { font-weight: 800; }
            @media (prefers-color-scheme: dark) {
              body { background: #111318; color: #eef2f7; }
              .panel { background: #1b2029; border-color: #303846; }
              p { color: #aeb7c7; }
            }
          </style>
        </head>
        <body>
          <main>
            <h1>AI/Dev environment completion</h1>
            <p>Installs or repairs Node 24 LTS, uv, pnpm, Python venv/pip support, adb/fastboot, and common CLI tools inside the KF Ubuntu workspace.</p>
            <div class="panel">
              <div>Phase: <span id="phase" class="status">loading</span></div>
              <div>Action: <span id="action">--</span></div>
              <div>Summary: <span id="summary">--</span></div>
              <div>Exit: <span id="exit">--</span></div>
              <div>Log: <span id="log">--</span></div>
              <div class="row">
                <button onclick="postAction('/toolchain/prepare')">Start one-click completion</button>
                <button class="secondary" onclick="postAction('/toolchain/doctor')">Run doctor</button>
              </div>
            </div>
            <div class="panel">
              <pre id="preview">Waiting for status...</pre>
            </div>
          </main>
          <script>
            async function postAction(path) {
              await fetch(path, { method: 'POST' });
              await refresh();
            }
            async function refresh() {
              try {
                const res = await fetch('/toolchain/status?ts=' + Date.now());
                const s = await res.json();
                document.getElementById('phase').textContent = s.phase || '--';
                document.getElementById('action').textContent = s.action || '--';
                document.getElementById('summary').textContent = s.summary || '--';
                document.getElementById('exit').textContent = (s.exitCode === null || s.exitCode === undefined) ? '--' : s.exitCode;
                document.getElementById('log').textContent = s.logPath || '--';
                document.getElementById('preview').textContent = s.outputPreview || 'No output yet.';
              } catch (e) {
                document.getElementById('phase').textContent = 'error';
                document.getElementById('preview').textContent = String(e);
              }
            }
            refresh();
            setInterval(refresh, 1500);
          </script>
        </body>
        </html>
    """.trimIndent()

    companion object {
        private const val DEFAULT_PORT = 8791
        private const val MAX_HEADER_BYTES = 16 * 1024
    }

    private data class HttpRequest(
        val requestLine: String,
        val body: String
    )
}
