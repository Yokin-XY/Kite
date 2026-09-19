package com.kite.app.foundation.service

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Supervisor XML-RPC 原生探测器：直接对 127.0.0.1 HTTP 端口调用
 * supervisor.getAllProcessInfo，不起任何 Linux 进程（PRoot supervisorctl 留作诊断兜底）。
 *
 * 协议：POST /RPC2，text/xml methodCall；响应为 methodResponse/params/array/struct 列表。
 * struct 字段：name/group/description/starttime/state(数字码)/statename(文字) 等。
 */
internal object SupervisordHttpProbe {
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 4_000
    private const val MAX_RESPONSE_BYTES = 512 * 1024

    /** supervisor ProcessStates 数字码 → supervisorctl 展示的文字态。 */
    private val STATE_NAMES = mapOf(
        10 to "RUNNING",
        20 to "BACKOFF",
        30 to "STARTING",
        40 to "EXITED",
        100 to "STOPPED",
        200 to "FATAL",
        1000 to "UNKNOWN",
    )

    sealed interface ProbeResult {
        /** 与 supervisorctl status 兼容的文本行（name STATE detail）。 */
        data class Ok(val statusLines: List<String>) : ProbeResult
        data class Unavailable(val reason: String) : ProbeResult
    }

    fun queryAllProcessInfo(port: Int): ProbeResult = runCatching {
        val body = ("<?xml version=\"1.0\"?>" +
            "<methodCall><methodName>supervisor.getAllProcessInfo</methodName></methodCall>")
            .toByteArray(StandardCharsets.UTF_8)
        val request = buildString {
            append("POST /RPC2 HTTP/1.1\r\n")
            append("Host: 127.0.0.1:$port\r\n")
            append("Content-Type: text/xml\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            socket.getOutputStream().apply {
                write(request)
                write(body)
                flush()
            }
            val raw = readCapped(socket)
            val text = raw.toString(StandardCharsets.UTF_8)
            val statusLine = text.lineSequence().firstOrNull().orEmpty()
            if (!statusLine.contains(" 200 ")) {
                return ProbeResult.Unavailable("http_status:${statusLine.take(40)}")
            }
            val payload = text.substringAfter("\r\n\r\n", "")
            if (!payload.contains("methodResponse")) {
                return ProbeResult.Unavailable("malformed_response")
            }
            if (payload.contains("<fault>")) {
                return ProbeResult.Unavailable("xmlrpc_fault")
            }
            val lines = parseProcessStructs(payload)
            if (lines.isEmpty()) ProbeResult.Unavailable("empty_process_list")
            else ProbeResult.Ok(lines)
        }
    }.getOrElse { error ->
        ProbeResult.Unavailable("probe_failed:${error.javaClass.simpleName}")
    }

    private fun readCapped(socket: Socket): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        socket.getInputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_RESPONSE_BYTES) break
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    /** 每个 struct 抽 name/statename(或 state 数字码)/description，拼 supervisorctl 兼容行。 */
    internal fun parseProcessStructs(payload: String): List<String> = payload
        .split("<struct>")
        .drop(1)
        .mapNotNull { struct ->
            val name = memberValue(struct, "name") ?: return@mapNotNull null
            val stateText = memberValue(struct, "statename")
                ?: memberValue(struct, "state")?.toIntOrNull()?.let(STATE_NAMES::get)
                ?: "UNKNOWN"
            val description = memberValue(struct, "description").orEmpty()
            "$name $stateText $description"
        }
        .filter { it.isNotBlank() }

    private fun memberValue(struct: String, member: String): String? {
        val anchor = "<member><name>$member</name><value>"
        val start = struct.indexOf(anchor)
        if (start < 0) return null
        val valueStart = start + anchor.length
        // 跳过类型开标签（<string>、<int> 等）；纯文本值没有标签。
        val openEnd = struct.indexOf('>', valueStart)
        val textStart = if (openEnd > valueStart && struct[valueStart] == '<') openEnd + 1 else valueStart
        val textEnd = struct.indexOf("</value>", textStart)
        if (textEnd < 0 || textEnd <= textStart) return null
        var value = struct.substring(textStart, textEnd)
        // 剥类型闭标签（</string> 等）。
        val close = value.lastIndexOf('>')
        if (close >= 0) {
            val open = value.lastIndexOf('<', close)
            if (open >= 0) value = value.substring(0, open)
        }
        return value.trim()
    }
}

/** 供单测读取解析行为；不暴露网络面。 */
internal object SupervisordHttpProbeAccess {
    internal fun parseProcessStructsForTest(payload: String): List<String> =
        SupervisordHttpProbe.parseProcessStructs(payload)
}
