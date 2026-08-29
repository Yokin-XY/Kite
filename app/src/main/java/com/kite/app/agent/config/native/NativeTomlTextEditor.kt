package com.kite.app.agent.config.native

/**
 * 只修改 Adapter 明确拥有的 TOML 键，保留注释、未知字段和其他原生表。
 * 
 * 这不是通用 TOML 序列化器；读取与最终校验仍由 tomlj 完成。
 */
internal class NativeTomlTextEditor(initial: String) {
    private val lines = initial.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList()

    val text: String
        get() = lines.joinToString("\n").trimEnd() + "\n"

    fun setRootString(key: String, value: String?) {
        val end = lines.indexOfFirst(::isHeader).let { if (it < 0) lines.size else it }
        setField(0, end, key, value?.let(::tomlString))
    }

    fun setTableFields(path: List<String>, fields: Map<String, String?>) {
        val range = tableRange(path)
        val start: Int
        val end: Int
        if (range == null) {
            appendBlankIfNeeded()
            lines += "[${renderPath(path)}]"
            start = lines.size
            end = lines.size
        } else {
            start = range.first + 1
            end = range.last + 1
        }
        var currentEnd = end
        fields.forEach { (key, rendered) ->
            currentEnd = setField(start, currentEnd, key, rendered)
        }
    }

    fun removeTableTree(path: List<String>) {
        var index = 0
        while (index < lines.size) {
            val header = parseHeader(lines[index])
            if (header != null && !header.array && header.path.startsWith(path)) {
                var end = index + 1
                while (end < lines.size && !isHeader(lines[end])) end++
                lines.subList(index, end).clear()
                while (index < lines.size && lines[index].isBlank() && lines.getOrNull(index - 1)?.isBlank() == true) {
                    lines.removeAt(index)
                }
            } else {
                index++
            }
        }
    }

    fun setNamedArrayTable(table: String, name: String, fields: Map<String, String?>) {
        val range = namedArrayTableRange(table, name)
        val start: Int
        val end: Int
        if (range == null) {
            appendBlankIfNeeded()
            lines += "[[$table]]"
            lines += "name = ${tomlString(name)}"
            start = lines.size
            end = lines.size
        } else {
            start = range.first + 1
            end = range.last + 1
        }
        var currentEnd = end
        fields.filterKeys { it != "name" }.forEach { (key, rendered) ->
            currentEnd = setField(start, currentEnd, key, rendered)
        }
    }

    fun removeNamedArrayTable(table: String, name: String) {
        val range = namedArrayTableRange(table, name) ?: return
        lines.subList(range.first, range.last + 1).clear()
    }

    private fun tableRange(path: List<String>): IntRange? {
        val start = lines.indices.firstOrNull { index ->
            parseHeader(lines[index])?.let { !it.array && it.path == path } == true
        } ?: return null
        var end = start + 1
        while (end < lines.size && !isHeader(lines[end])) end++
        return start until end
    }

    private fun namedArrayTableRange(table: String, name: String): IntRange? {
        var index = 0
        while (index < lines.size) {
            val header = parseHeader(lines[index])
            if (header?.array == true && header.path == listOf(table)) {
                var end = index + 1
                while (end < lines.size && !isHeader(lines[end])) end++
                val foundName = (index + 1 until end).firstNotNullOfOrNull { lineIndex ->
                    FIELD.matchEntire(lines[lineIndex])?.takeIf { it.groupValues[1] == "name" }
                        ?.groupValues?.get(2)?.let(::parseTomlString)
                }
                if (foundName == name) return index until end
                index = end
            } else {
                index++
            }
        }
        return null
    }

    private fun setField(start: Int, initialEnd: Int, key: String, rendered: String?): Int {
        val index = (start until minOf(initialEnd, lines.size)).firstOrNull { lineIndex ->
            FIELD.matchEntire(lines[lineIndex])?.groupValues?.get(1) == key
        }
        if (index != null) {
            if (rendered == null) {
                lines.removeAt(index)
                return initialEnd - 1
            }
            val comment = lines[index].substringAfter('#', "").takeIf(String::isNotBlank)?.let { " #$it" }.orEmpty()
            lines[index] = "$key = $rendered$comment"
            return initialEnd
        }
        if (rendered != null) {
            lines.add(minOf(initialEnd, lines.size), "$key = $rendered")
            return initialEnd + 1
        }
        return initialEnd
    }

    private fun appendBlankIfNeeded() {
        while (lines.size > 1 && lines.last().isBlank() && lines[lines.lastIndex - 1].isBlank()) lines.removeLast()
        if (lines.any(String::isNotBlank) && lines.lastOrNull()?.isNotBlank() == true) lines += ""
    }

    private data class Header(val array: Boolean, val path: List<String>)

    private fun parseHeader(line: String): Header? {
        val trimmed = line.trim()
        val array = trimmed.startsWith("[[") && trimmed.endsWith("]]" )
        val regular = trimmed.startsWith('[') && trimmed.endsWith(']') && !array
        if (!array && !regular) return null
        val body = if (array) trimmed.substring(2, trimmed.length - 2) else trimmed.substring(1, trimmed.length - 1)
        val path = splitPath(body) ?: return null
        return Header(array, path)
    }

    private fun splitPath(value: String): List<String>? {
        val result = mutableListOf<String>()
        val part = StringBuilder()
        var quoted = false
        var escaped = false
        value.forEach { char ->
            when {
                escaped -> { part.append(char); escaped = false }
                quoted && char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                char == '.' && !quoted -> { result += part.toString().trim(); part.clear() }
                else -> part.append(char)
            }
        }
        if (quoted || escaped) return null
        result += part.toString().trim()
        return result.takeIf { it.all(String::isNotBlank) }
    }

    private fun renderPath(path: List<String>): String = path.joinToString(".") { segment ->
        if (BARE_KEY.matches(segment)) segment else tomlString(segment)
    }

    companion object {
        fun tomlString(value: String): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }

        fun tomlStringArray(values: List<String>): String = values.joinToString(", ", "[", "]", transform = ::tomlString)

        private fun parseTomlString(value: String): String? {
            val trimmed = value.substringBefore('#').trim()
            if (trimmed.length < 2 || trimmed.first() != '"' || trimmed.last() != '"') return null
            return runCatching {
                val result = StringBuilder()
                var escaped = false
                trimmed.substring(1, trimmed.lastIndex).forEach { char ->
                    if (escaped) {
                        result.append(when (char) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> char })
                        escaped = false
                    } else if (char == '\\') escaped = true else result.append(char)
                }
                if (escaped) error("invalid escape")
                result.toString()
            }.getOrNull()
        }

        private fun isHeader(line: String): Boolean = line.trim().startsWith('[')
        private val FIELD = Regex("\\s*([A-Za-z0-9_-]+)\\s*=\\s*(.*?)\\s*")
        private val BARE_KEY = Regex("[A-Za-z0-9_-]+")
    }
}

private fun List<String>.startsWith(prefix: List<String>): Boolean =
    size >= prefix.size && subList(0, prefix.size) == prefix
