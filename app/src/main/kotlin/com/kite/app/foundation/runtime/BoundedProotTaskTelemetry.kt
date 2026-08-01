package com.kite.app.foundation.runtime

internal enum class BoundedProotTaskResultCategory {
    SUCCEEDED,
    EXIT_NON_ZERO,
    TIMED_OUT,
    CANCELLED,
    ADMISSION_REJECTED,
    EXECUTION_FAILED,
    FALLBACK_FAILED,
}

internal enum class BoundedProotLatencyBucket(val envName: String) {
    LE_10_MS("le_10_ms"),
    LE_50_MS("le_50_ms"),
    LE_100_MS("le_100_ms"),
    LE_250_MS("le_250_ms"),
    LE_500_MS("le_500_ms"),
    LE_1000_MS("le_1000_ms"),
    LE_5000_MS("le_5000_ms"),
    GT_5000_MS("gt_5000_ms");

    companion object {
        fun forDuration(durationMs: Long): BoundedProotLatencyBucket = when {
            durationMs <= 10L -> LE_10_MS
            durationMs <= 50L -> LE_50_MS
            durationMs <= 100L -> LE_100_MS
            durationMs <= 250L -> LE_250_MS
            durationMs <= 500L -> LE_500_MS
            durationMs <= 1_000L -> LE_1000_MS
            durationMs <= 5_000L -> LE_5000_MS
            else -> GT_5000_MS
        }
    }
}

internal data class BoundedProotTelemetryKey(
    val lane: RuntimeLaneKind,
    val route: WarmProotExecutionRoute,
    val result: BoundedProotTaskResultCategory,
)

internal data class BoundedProotLatencySnapshot(
    val sumMs: Long,
    val maxMs: Long,
    val buckets: Map<BoundedProotLatencyBucket, Long>,
)

internal data class BoundedProotTelemetryEntrySnapshot(
    val key: BoundedProotTelemetryKey,
    val count: Long,
    val queue: BoundedProotLatencySnapshot,
    val execute: BoundedProotLatencySnapshot,
    val total: BoundedProotLatencySnapshot,
)

internal data class BoundedProotTaskTelemetrySnapshot(
    val entries: List<BoundedProotTelemetryEntrySnapshot>,
) {
    val sampleCount: Long get() = entries.sumOf { it.count }

    fun toRuntimeHealthEnvText(): String = buildString {
        appendLine("proot_bounded_telemetry_schema=bounded_proot_task_telemetry_v1")
        appendLine("proot_bounded_telemetry_source=bounded_proot_task_executor")
        appendLine("proot_bounded_telemetry_scope=actual_completed_attempts")
        appendLine("proot_bounded_telemetry_sample_count=$sampleCount")
        appendLine("proot_bounded_telemetry_entry_count=${entries.size}")
        entries.forEachIndexed { index, entry ->
            val prefix = "proot_bounded_telemetry_entry_${index + 1}"
            appendLine("${prefix}_lane=${entry.key.lane.name}")
            appendLine("${prefix}_route=${entry.key.route.name}")
            appendLine("${prefix}_result=${entry.key.result.name}")
            appendLine("${prefix}_count=${entry.count}")
            appendLatency(prefix, "queue", entry.queue)
            appendLatency(prefix, "execute", entry.execute)
            appendLatency(prefix, "total", entry.total)
        }
        appendLine("proot_bounded_telemetry_boundary=low_cardinality_enums_and_numbers_only")
    }

    private fun StringBuilder.appendLatency(
        prefix: String,
        phase: String,
        latency: BoundedProotLatencySnapshot,
    ) {
        appendLine("${prefix}_${phase}_ms_sum=${latency.sumMs}")
        appendLine("${prefix}_${phase}_ms_max=${latency.maxMs}")
        BoundedProotLatencyBucket.entries.forEach { bucket ->
            appendLine("${prefix}_${phase}_bucket_${bucket.envName}=${latency.buckets[bucket] ?: 0L}")
        }
    }
}

internal class BoundedProotTaskTelemetryCollector {
    private class MutableLatency {
        var sumMs: Long = 0L
        var maxMs: Long = 0L
        val buckets = LongArray(BoundedProotLatencyBucket.entries.size)

        fun record(durationMs: Long) {
            val safeDuration = durationMs.coerceAtLeast(0L)
            sumMs += safeDuration
            maxMs = maxOf(maxMs, safeDuration)
            buckets[BoundedProotLatencyBucket.forDuration(safeDuration).ordinal] += 1L
        }

        fun snapshot(): BoundedProotLatencySnapshot = BoundedProotLatencySnapshot(
            sumMs = sumMs,
            maxMs = maxMs,
            buckets = BoundedProotLatencyBucket.entries.associateWith { buckets[it.ordinal] },
        )
    }

    private class MutableEntry {
        var count: Long = 0L
        val queue = MutableLatency()
        val execute = MutableLatency()
        val total = MutableLatency()
    }

    private val lock = Any()
    private val entries = linkedMapOf<BoundedProotTelemetryKey, MutableEntry>()

    fun record(lane: RuntimeLaneKind, execution: WarmProotPoolExecution) {
        val key = BoundedProotTelemetryKey(
            lane = lane,
            route = execution.route,
            result = execution.resultCategory(),
        )
        synchronized(lock) {
            val entry = entries.getOrPut(key, ::MutableEntry)
            entry.count += 1L
            entry.queue.record(execution.queueWaitMs)
            entry.execute.record(execution.executeMs)
            entry.total.record(execution.totalMs)
        }
    }

    fun snapshot(): BoundedProotTaskTelemetrySnapshot = synchronized(lock) {
        BoundedProotTaskTelemetrySnapshot(
            entries = entries.entries
                .sortedWith(
                    compareBy<Map.Entry<BoundedProotTelemetryKey, MutableEntry>> { it.key.lane.ordinal }
                        .thenBy { it.key.route.ordinal }
                        .thenBy { it.key.result.ordinal }
                )
                .map { (key, value) ->
                    BoundedProotTelemetryEntrySnapshot(
                        key = key,
                        count = value.count,
                        queue = value.queue.snapshot(),
                        execute = value.execute.snapshot(),
                        total = value.total.snapshot(),
                    )
                },
        )
    }
}

internal object BoundedProotTaskTelemetry {
    private val collector = BoundedProotTaskTelemetryCollector()

    fun record(lane: RuntimeLaneKind, execution: WarmProotPoolExecution) {
        collector.record(lane, execution)
    }

    fun snapshot(): BoundedProotTaskTelemetrySnapshot = collector.snapshot()
}

internal fun WarmProotPoolExecution.resultCategory(): BoundedProotTaskResultCategory {
    val result = execution
    return when {
        route == WarmProotExecutionRoute.ADMISSION_REJECTED ->
            BoundedProotTaskResultCategory.ADMISSION_REJECTED
        route == WarmProotExecutionRoute.FALLBACK_FAILED && result == null ->
            BoundedProotTaskResultCategory.FALLBACK_FAILED
        result?.succeeded == true -> BoundedProotTaskResultCategory.SUCCEEDED
        result?.timedOut == true -> BoundedProotTaskResultCategory.TIMED_OUT
        result?.cancelled == true -> BoundedProotTaskResultCategory.CANCELLED
        result?.exitCode != null || result?.termSignal != null ->
            BoundedProotTaskResultCategory.EXIT_NON_ZERO
        else -> BoundedProotTaskResultCategory.EXECUTION_FAILED
    }
}
