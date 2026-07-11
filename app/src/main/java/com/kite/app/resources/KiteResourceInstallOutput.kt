package com.kite.app.resources

object KiteResourceInstallOutput {
    private const val FAILURE = "KITE_RESOURCE_FAILURE "
    private const val HEARTBEAT = "KITE_RESOURCE_HEARTBEAT "
    private const val RETRY = "KITE_RESOURCE_RETRY "
    private const val STEP = "KITE_RESOURCE_STEP "

    fun isFailure(line: String): Boolean = line.startsWith(FAILURE)

    fun summary(line: String): String? = when {
        line.startsWith(FAILURE) -> failureSummary(line)
        line.startsWith(RETRY) -> retrySummary(line)
        line.startsWith(HEARTBEAT) -> heartbeatSummary(line)
        line.startsWith(STEP) -> stepSummary(line.removePrefix(STEP))
        else -> null
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

    private fun heartbeatSummary(line: String): String {
        val stage = value(line, "stage")
        val elapsed = value(line, "elapsed")
        val label = when (stage) {
            "acquire" -> "资源仍在下载"
            "verify" -> "安装结果仍在验证"
            else -> "安装器仍在运行"
        }
        return elapsed?.let { "$label（已运行 $it 秒）" } ?: label
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
}
