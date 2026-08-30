package com.kite.app.resources

object KiteResourceInstallOutput {
    private const val FAILURE = "KITE_RESOURCE_FAILURE "
    private const val HEARTBEAT = "KITE_RESOURCE_HEARTBEAT "
    private const val RETRY = "KITE_RESOURCE_RETRY "
    private const val STEP = "KITE_RESOURCE_STEP "

    fun isFailure(line: String): Boolean = line.startsWith(FAILURE)

    fun isHeartbeat(line: String): Boolean = line.trimStart().startsWith(HEARTBEAT)

    fun isProtocolLine(line: String): Boolean = line.trimStart().startsWith("KITE_RESOURCE_")

    fun summary(line: String): String? = when {
        line.startsWith(FAILURE) -> failureSummary(line)
        line.startsWith(RETRY) -> retrySummary(line)
        line.startsWith(HEARTBEAT) -> null
        line.startsWith(STEP) -> stepSummary(line.removePrefix(STEP))
        else -> null
    }

    /** 清除终端样式与控制字符，只保留适合卡片单行展示的状态文本。 */
    fun compactProgress(raw: String): String {
        val compact = raw
            .replace(ANSI_ESCAPE, "")
            .replace(CONTROL_CHARACTER, "")
            .replace(WHITESPACE, " ")
            .trim()
        if (compact.isBlank() || isHeartbeat(compact)) return ""
        return summary(compact)
            ?: compact.takeUnless(::isProtocolLine).orEmpty()
    }

    /**
     * 将持久化的原始 SH 输出投影为用户可读报告。
     *
     * 原始输出仍完整保留；这里只模拟终端的回车重绘和退格语义，并隐藏 Kite 内部协议行。
     */
    fun userVisibleReport(raw: String): String {
        if (raw.isBlank()) return ""
        val terminalLines = terminalLines(raw.replace(ANSI_ESCAPE, ""))
        val visible = buildList {
            terminalLines.forEach { rawLine ->
                val line = rawLine.trimEnd()
                val trimmed = line.trim()
                if (trimmed.isBlank() || isInternalProcessMarker(trimmed) || isHeartbeat(trimmed)) return@forEach
                val projected = summary(trimmed)
                    ?: trimmed.takeUnless(::isProtocolLine)
                    ?: return@forEach
                if (lastOrNull() != projected) add(projected)
            }
        }
        return visible.joinToString("\n").trim()
    }

    /** 从实时 SH 尾部提取用户可读的下载细节；未知输出保持为空，不猜进度。 */
    fun progressDetail(output: String): String? {
        if (output.isBlank()) return null
        val normalized = output.replace('\r', '\n')
        val candidates = buildList {
            GIT_PROGRESS.findAll(normalized).forEach { match ->
                val phase = when (match.groupValues[1]) {
                    "Receiving objects" -> "源码下载"
                    "Resolving deltas" -> "源码整理"
                    else -> "源码写入"
                }
                add(match.range.first to "$phase ${match.groupValues[2]}%")
            }
            APT_DOWNLOAD.findAll(normalized).forEach { match ->
                val host = sourceHost(match.groupValues[2])
                val size = match.groupValues[3].trim()
                val suffix = size.takeIf(String::isNotBlank)?.let { "（$it）" }.orEmpty()
                add(match.range.first to "正在从 $host 下载第 ${match.groupValues[1]} 个安装组件$suffix")
            }
            SOURCE.findAll(normalized).forEach { match ->
                add(match.range.first to "正在从 ${sourceHost(match.groupValues[1])} 获取资源")
            }
        }
        return candidates.maxByOrNull { it.first }?.second
    }

    private fun failureSummary(line: String): String {
        val stage = value(line, "stage")
        val exit = value(line, "exit")
        val reason = value(line, "reason")
        val detail = listOfNotNull(
            exit?.let { "退出码 $it" },
            reason?.let { "原因 $it" }
        ).joinToString("，")
        val message = when (stage) {
            "acquire" -> "资源下载失败，网络或资源来源暂时不可用"
            "install" -> "安装器执行失败"
            "prepare" -> "安装前置环境不满足"
            "verify", "verify-download" -> "安装结果验证失败"
            else -> "资源安装失败"
        }
        return if (detail.isBlank()) message else "$message（$detail）"
    }

    private fun retrySummary(line: String): String {
        val attempt = value(line, "attempt")
        val exit = value(line, "exit")
        return buildString {
            append("网络出现波动，正在重试")
            attempt?.let { append("（第 ").append(it).append(" 次") }
            exit?.let {
                if (attempt == null) append('（') else append("，")
                append("退出码 ").append(it)
            }
            if (attempt != null || exit != null) append('）')
        }
    }

    private fun stepSummary(payload: String): String? {
        val operation = payload.substringBefore(' ')
        return when (operation) {
            "acquire" -> "正在下载资源"
            "acquire-complete" -> "资源下载完成"
            "install" -> "正在执行安装器"
            "install-complete" -> "安装器执行完成"
            "verify", "verify-install" -> "正在验证安装结果"
            "verify-complete" -> "安装结果验证通过"
            "commit-install" -> "正在登记资源"
            "rollback-install" -> "安装失败，正在恢复原有版本"
            "recover-interrupted-install" -> "正在恢复上次中断前的可用版本"
            else -> null
        }
    }

    private fun value(line: String, key: String): String? =
        TOKEN.findAll(line)
            .firstOrNull { it.groupValues[1] == key }
            ?.groupValues
            ?.getOrNull(2)
            ?.takeIf { it.isNotBlank() }

    private val TOKEN = Regex("(?:^|\\s)([A-Za-z0-9_-]+)=([^\\s]+)")
    private val ANSI_ESCAPE = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
    private val CONTROL_CHARACTER = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
    private val WHITESPACE = Regex("\\s+")
    private val GIT_PROGRESS = Regex("(Receiving objects|Resolving deltas|Updating files):\\s+(\\d{1,3})%")
    private val APT_DOWNLOAD = Regex("(?m)^Get:(\\d+)\\s+(https?://\\S+).*?\\[([^]\\r\\n]+)]")
    private val SOURCE = Regex("(?:source|url)=(https?://[^\\s]+)")

    private fun terminalLines(raw: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < raw.length) {
            when (val character = raw[index]) {
                '\r' -> {
                    if (index + 1 < raw.length && raw[index + 1] == '\n') {
                        lines += current.toString()
                        current.clear()
                        index += 1
                    } else {
                        current.clear()
                    }
                }
                '\n' -> {
                    lines += current.toString()
                    current.clear()
                }
                '\b' -> if (current.isNotEmpty()) current.deleteCharAt(current.lastIndex)
                else -> if (!Character.isISOControl(character.code) || character == '\t') current.append(character)
            }
            index += 1
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun isInternalProcessMarker(line: String): Boolean =
        line.startsWith("__kite_root_pid:") ||
            line.startsWith("__kite_process_group_id:") ||
            line.startsWith("__kite_system_session_id:")

    private fun sourceHost(raw: String): String = runCatching {
        java.net.URI(raw).host?.takeIf(String::isNotBlank)
    }.getOrNull() ?: raw.substringAfter("://").substringBefore('/').ifBlank { raw }
}
