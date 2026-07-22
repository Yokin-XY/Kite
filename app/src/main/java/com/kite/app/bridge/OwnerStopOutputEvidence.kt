package com.kite.app.bridge

/** 将底层 owner 关停标记收敛成 Bridge 可使用的确认事实。 */
internal object OwnerStopOutputEvidence {
    private val settledOwnerOutcomes = setOf("CONFIRMED", "OWNER_NOT_FOUND")

    fun isConfirmed(output: String): Boolean =
        !hasRemaining(output) && !hasUnconfirmedOwnerOutcome(output)

    fun hasRemaining(output: String): Boolean = remainingProcessIds(output).isNotEmpty()

    fun remainingProcessIds(output: String): List<String> = output
        .lineSequence()
        .filter {
            it.startsWith("__kite_stop_remaining:") ||
                it.startsWith("__kite_stop_remaining_pgid:")
        }
        .flatMap { line -> line.substringAfter(':').split(',').asSequence() }
        .map(String::trim)
        .filter { it.matches(Regex("\\d+")) }
        .distinct()
        .toList()

    fun hasUnconfirmedOwnerOutcome(output: String): Boolean = output
        .lineSequence()
        .filter { it.startsWith("__kite_owner_stop_outcome:") }
        .map { it.substringAfter(':').trim() }
        .any { it !in settledOwnerOutcomes }

    fun userMessage(output: String): String? {
        val remaining = remainingProcessIds(output)
        if (remaining.isNotEmpty()) {
            return "停止后仍有进程残留：${remaining.joinToString(",")}"
        }
        val outcomes = output
            .lineSequence()
            .filter { it.startsWith("__kite_owner_stop_outcome:") }
            .map { it.substringAfter(':').trim() }
            .filter(String::isNotBlank)
            .toList()
        if (outcomes.isEmpty()) return null
        if (outcomes.all { it == "OWNER_NOT_FOUND" }) return "已关闭"
        val unconfirmed = outcomes.firstOrNull { it !in settledOwnerOutcomes }
            ?: return "已停止，未发现进程残留"
        return when (unconfirmed) {
            "TELEMETRY_UNAVAILABLE" -> "运行记录暂不完整，未执行强制停止"
            "PROBE_UNAVAILABLE" -> "无法确认实例进程状态，未标记为已停止"
            "STILL_RUNNING" -> "停止后仍观测到运行进程"
            "TIMEOUT" -> "停止结果暂时无法核验"
            else -> "停止失败，请稍后重试"
        }
    }
}
