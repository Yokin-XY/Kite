package com.kite.app.bridge

/** 将底层 owner 关停标记收敛成 Bridge 可使用的确认事实。 */
internal object OwnerStopOutputEvidence {
    fun isConfirmed(output: String): Boolean =
        !hasRemaining(output) && !hasUnconfirmedOwnerOutcome(output)

    fun hasRemaining(output: String): Boolean = output
        .lineSequence()
        .filter {
            it.startsWith("__kite_stop_remaining:") ||
                it.startsWith("__kite_stop_remaining_pgid:")
        }
        .any { line ->
            line.substringAfter(':')
                .split(',')
                .any { value -> value.trim().matches(Regex("\\d+")) }
        }

    fun hasUnconfirmedOwnerOutcome(output: String): Boolean = output
        .lineSequence()
        .filter { it.startsWith("__kite_owner_stop_outcome:") }
        .map { it.substringAfter(':').trim() }
        .any { it != "CONFIRMED" }
}
