package com.kite.app.foundation.runtime

import java.net.URI

internal data class RuntimeHttpProxyRequest(
    val method: String,
    val host: String,
    val port: Int,
    val forwardedHead: ByteArray?,
) {
    val isConnect: Boolean
        get() = method.equals("CONNECT", ignoreCase = true)

    companion object {
        fun parse(head: ByteArray): RuntimeHttpProxyRequest? {
            val text = head.toString(Charsets.ISO_8859_1)
            val lineSeparator = if (text.contains("\r\n")) "\r\n" else "\n"
            val lines = text.split(lineSeparator)
            val requestLine = lines.firstOrNull()?.trim().orEmpty()
            val parts = requestLine.split(Regex("\\s+"), limit = 3)
            if (parts.size != 3) return null
            val method = parts[0]
            val target = parts[1]
            if (method.equals("CONNECT", ignoreCase = true)) {
                val authority = parseAuthority(target, 443) ?: return null
                return RuntimeHttpProxyRequest(method, authority.first, authority.second, null)
            }

            val uri = runCatching { URI(target) }.getOrNull()
                ?.takeIf { it.isAbsolute && !it.host.isNullOrBlank() }
                ?: return null
            val port = when {
                uri.port > 0 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                else -> 80
            }
            val path = buildString {
                append(uri.rawPath?.takeIf(String::isNotEmpty) ?: "/")
                uri.rawQuery?.let { append('?').append(it) }
            }
            val forwardedLines = buildList {
                add("$method $path ${parts[2]}")
                lines.drop(1)
                    .filterNot { it.startsWith("Proxy-Connection:", ignoreCase = true) }
                    .forEach(::add)
            }
            return RuntimeHttpProxyRequest(
                method = method,
                host = uri.host,
                port = port,
                forwardedHead = forwardedLines.joinToString(lineSeparator).toByteArray(Charsets.ISO_8859_1),
            )
        }

        private fun parseAuthority(value: String, defaultPort: Int): Pair<String, Int>? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith("[")) {
                val closing = trimmed.indexOf(']')
                if (closing <= 1) return null
                val host = trimmed.substring(1, closing)
                val port = trimmed.substring(closing + 1)
                    .removePrefix(":")
                    .takeIf(String::isNotBlank)
                    ?.toIntOrNull()
                    ?: defaultPort
                if (port !in 1..65535) return null
                return host to port
            }
            val host = trimmed.substringBeforeLast(':', trimmed)
            val port = if (host == trimmed) defaultPort else trimmed.substringAfterLast(':').toIntOrNull()
            if (host.isBlank() || port == null || port !in 1..65535) return null
            return host to port
        }
    }
}
