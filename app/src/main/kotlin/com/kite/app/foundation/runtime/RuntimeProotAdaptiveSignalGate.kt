package com.kite.app.foundation.runtime

internal enum class RuntimeProotThermalEvidence {
    UNAVAILABLE,
    TRUSTED_NORMAL,
    TRUSTED_HOT,
}

internal data class RuntimeProotThermalSignal(
    val evidence: RuntimeProotThermalEvidence = RuntimeProotThermalEvidence.UNAVAILABLE,
    val observedAtMs: Long = 0L,
)

internal data class RuntimeProotTelemetryReading(
    val capturedAtMs: Long,
    val snapshot: BoundedProotTaskTelemetrySnapshot,
)

internal enum class RuntimeProotAdaptiveWindowAction {
    HOLD,
    DOWNGRADE_ONE,
    PROMOTION_WINDOW_ELIGIBLE,
}

internal enum class RuntimeProotAdaptiveWindowReason {
    CLOCK_INVALID,
    POLICY_SOURCE_NOT_ACTUAL,
    PROFILE_NOT_ADAPTIVE,
    PROFILE_LIMIT_CONFLICT,
    WINDOW_TIME_INVALID,
    WINDOW_TOO_SHORT,
    WINDOW_TOO_LONG,
    WINDOW_STALE,
    TELEMETRY_INVALID,
    TELEMETRY_COUNTER_REGRESSION,
    TELEMETRY_SAMPLE_INSUFFICIENT,
    MEMORY_SIGNAL_UNKNOWN,
    MEMORY_PRESSURE_ELEVATED,
    MEMORY_PRESSURE_HIGH,
    FAILURE_RATE_HIGH,
    THERMAL_SIGNAL_UNAVAILABLE,
    THERMAL_SIGNAL_STALE,
    THERMAL_PRESSURE_HIGH,
    APP_NOT_FOREGROUND,
    CALIBRATION_GUARD_NOT_READY,
    LATENCY_TOO_HIGH,
    HEALTHY_WINDOW,
    ALREADY_HIGHEST,
}

internal data class RuntimeProotAdaptiveWindowDecision(
    val scope: String = "planned_not_production",
    val action: RuntimeProotAdaptiveWindowAction,
    val reason: RuntimeProotAdaptiveWindowReason,
    val currentConfiguredGlobalMax: Int,
    val suggestedConfiguredGlobalMax: Int,
    val sampleCount: Long = 0L,
    val failureCount: Long = 0L,
    val failureRatePermille: Int = 0,
    val totalLatencyP95Bucket: BoundedProotLatencyBucket? = null,
    val changesCoordinator: Boolean = false,
)

internal data class RuntimeProotAdaptiveSignalInput(
    val nowMs: Long,
    val tuning: WarmProotExecutionCoordinator.TuningSnapshot,
    val calibration: RuntimeProotCalibrationAlignmentResult,
    val previousTelemetry: RuntimeProotTelemetryReading,
    val currentTelemetry: RuntimeProotTelemetryReading,
    val thermal: RuntimeProotThermalSignal = RuntimeProotThermalSignal(),
)

/**
 * 把现有正式状态和累计遥测归一成单个候选窗口。
 *
 * 这里只给 planned 建议；连续窗口、冷却和真正的策略变更属于后续状态机与生产接线。
 */
internal object RuntimeProotAdaptiveSignalGate {
    internal const val MIN_WINDOW_MS = 30_000L
    internal const val MAX_WINDOW_MS = 10 * 60_000L
    internal const val MAX_WINDOW_AGE_MS = 2 * 60_000L
    internal const val MAX_THERMAL_AGE_MS = 60_000L
    internal const val MIN_PROMOTION_SAMPLES = 20L
    internal const val MIN_FAILURE_DECISION_SAMPLES = 5L
    internal const val HIGH_FAILURE_RATE_PERMILLE = 100
    internal val MAX_PROMOTION_P95_BUCKET = BoundedProotLatencyBucket.LE_1000_MS

    fun evaluate(input: RuntimeProotAdaptiveSignalInput): RuntimeProotAdaptiveWindowDecision {
        val currentMax = input.tuning.configuredGlobalMax
        val profileLimits = RuntimeProotCalibrationAlignment.productionProfileLimits()
        fun hold(
            reason: RuntimeProotAdaptiveWindowReason,
            delta: TelemetryDelta? = null,
        ) = decision(
            action = RuntimeProotAdaptiveWindowAction.HOLD,
            reason = reason,
            currentMax = currentMax,
            suggestedMax = currentMax,
            delta = delta,
        )

        if (input.nowMs <= 0L) return hold(RuntimeProotAdaptiveWindowReason.CLOCK_INVALID)
        if (input.tuning.policySource != WarmProotPolicySource.RUNTIME_HEALTH) {
            return hold(RuntimeProotAdaptiveWindowReason.POLICY_SOURCE_NOT_ACTUAL)
        }

        val expectedMax = expectedMax(input.tuning.profileGroup, profileLimits)
            ?: return hold(RuntimeProotAdaptiveWindowReason.PROFILE_NOT_ADAPTIVE)
        if (expectedMax != currentMax) {
            return hold(RuntimeProotAdaptiveWindowReason.PROFILE_LIMIT_CONFLICT)
        }

        val previousAt = input.previousTelemetry.capturedAtMs
        val currentAt = input.currentTelemetry.capturedAtMs
        val windowMs = currentAt - previousAt
        if (previousAt <= 0L || currentAt <= previousAt || currentAt > input.nowMs) {
            return hold(RuntimeProotAdaptiveWindowReason.WINDOW_TIME_INVALID)
        }
        if (windowMs < MIN_WINDOW_MS) return hold(RuntimeProotAdaptiveWindowReason.WINDOW_TOO_SHORT)
        if (windowMs > MAX_WINDOW_MS) return hold(RuntimeProotAdaptiveWindowReason.WINDOW_TOO_LONG)
        if (input.nowMs - currentAt > MAX_WINDOW_AGE_MS) {
            return hold(RuntimeProotAdaptiveWindowReason.WINDOW_STALE)
        }

        if (
            input.tuning.pressure == RuntimePressureLevel.HIGH ||
            input.tuning.pressure == RuntimePressureLevel.CRITICAL
        ) {
            return downgradeOrHoldAtLowest(
                reason = RuntimeProotAdaptiveWindowReason.MEMORY_PRESSURE_HIGH,
                currentMax = currentMax,
                profileLimits = profileLimits,
            )
        }

        val thermalStatus = thermalStatus(input.thermal, input.nowMs)
        if (thermalStatus == ThermalStatus.HOT) {
            return downgradeOrHoldAtLowest(
                reason = RuntimeProotAdaptiveWindowReason.THERMAL_PRESSURE_HIGH,
                currentMax = currentMax,
                profileLimits = profileLimits,
            )
        }

        val deltaResult = telemetryDelta(
            previous = input.previousTelemetry.snapshot,
            current = input.currentTelemetry.snapshot,
        )
        if (deltaResult.reason != null) return hold(deltaResult.reason)
        val delta = checkNotNull(deltaResult.delta)

        if (
            delta.sampleCount >= MIN_FAILURE_DECISION_SAMPLES &&
            delta.failureRatePermille >= HIGH_FAILURE_RATE_PERMILLE
        ) {
            return downgradeOrHoldAtLowest(
                reason = RuntimeProotAdaptiveWindowReason.FAILURE_RATE_HIGH,
                currentMax = currentMax,
                profileLimits = profileLimits,
                delta = delta,
            )
        }

        if (input.tuning.pressure == RuntimePressureLevel.UNKNOWN) {
            return hold(RuntimeProotAdaptiveWindowReason.MEMORY_SIGNAL_UNKNOWN, delta)
        }
        if (input.tuning.pressure == RuntimePressureLevel.ELEVATED) {
            return hold(RuntimeProotAdaptiveWindowReason.MEMORY_PRESSURE_ELEVATED, delta)
        }
        if (delta.sampleCount < MIN_PROMOTION_SAMPLES) {
            return hold(RuntimeProotAdaptiveWindowReason.TELEMETRY_SAMPLE_INSUFFICIENT, delta)
        }
        when (thermalStatus) {
            ThermalStatus.UNAVAILABLE ->
                return hold(RuntimeProotAdaptiveWindowReason.THERMAL_SIGNAL_UNAVAILABLE, delta)
            ThermalStatus.STALE ->
                return hold(RuntimeProotAdaptiveWindowReason.THERMAL_SIGNAL_STALE, delta)
            ThermalStatus.HOT -> error("hot thermal signal was handled before telemetry")
            ThermalStatus.NORMAL -> Unit
        }
        if (!input.tuning.foreground) {
            return hold(RuntimeProotAdaptiveWindowReason.APP_NOT_FOREGROUND, delta)
        }
        if (!input.calibration.eligibleAsSafetyGuard) {
            return hold(RuntimeProotAdaptiveWindowReason.CALIBRATION_GUARD_NOT_READY, delta)
        }
        if (
            delta.totalLatencyP95Bucket == null ||
            delta.totalLatencyP95Bucket.ordinal > MAX_PROMOTION_P95_BUCKET.ordinal
        ) {
            return hold(RuntimeProotAdaptiveWindowReason.LATENCY_TOO_HIGH, delta)
        }
        if (currentMax >= profileLimits.highPerformance) {
            return hold(RuntimeProotAdaptiveWindowReason.ALREADY_HIGHEST, delta)
        }

        return decision(
            action = RuntimeProotAdaptiveWindowAction.PROMOTION_WINDOW_ELIGIBLE,
            reason = RuntimeProotAdaptiveWindowReason.HEALTHY_WINDOW,
            currentMax = currentMax,
            suggestedMax = nextHigher(currentMax, profileLimits),
            delta = delta,
        )
    }

    private fun expectedMax(
        profile: RuntimeLifecyclePolicyProfileGroup,
        limits: RuntimeProotProductionProfileLimits,
    ): Int? = when (profile) {
        RuntimeLifecyclePolicyProfileGroup.LOW_POWER -> limits.lowPower
        RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED -> limits.balanced
        RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE -> limits.highPerformance
        RuntimeLifecyclePolicyProfileGroup.CUSTOM -> null
    }

    private fun nextHigher(
        currentMax: Int,
        limits: RuntimeProotProductionProfileLimits,
    ): Int = when (currentMax) {
        limits.lowPower -> limits.balanced
        limits.balanced -> limits.highPerformance
        else -> currentMax
    }

    private fun nextLower(
        currentMax: Int,
        limits: RuntimeProotProductionProfileLimits,
    ): Int = when (currentMax) {
        limits.highPerformance -> limits.balanced
        limits.balanced -> limits.lowPower
        else -> currentMax
    }

    private fun downgradeOrHoldAtLowest(
        reason: RuntimeProotAdaptiveWindowReason,
        currentMax: Int,
        profileLimits: RuntimeProotProductionProfileLimits,
        delta: TelemetryDelta? = null,
    ): RuntimeProotAdaptiveWindowDecision {
        val suggestedMax = nextLower(currentMax, profileLimits)
        return decision(
            action = if (suggestedMax < currentMax) {
                RuntimeProotAdaptiveWindowAction.DOWNGRADE_ONE
            } else {
                RuntimeProotAdaptiveWindowAction.HOLD
            },
            reason = reason,
            currentMax = currentMax,
            suggestedMax = suggestedMax,
            delta = delta,
        )
    }

    private fun decision(
        action: RuntimeProotAdaptiveWindowAction,
        reason: RuntimeProotAdaptiveWindowReason,
        currentMax: Int,
        suggestedMax: Int,
        delta: TelemetryDelta?,
    ) = RuntimeProotAdaptiveWindowDecision(
        action = action,
        reason = reason,
        currentConfiguredGlobalMax = currentMax,
        suggestedConfiguredGlobalMax = suggestedMax,
        sampleCount = delta?.sampleCount ?: 0L,
        failureCount = delta?.failureCount ?: 0L,
        failureRatePermille = delta?.failureRatePermille ?: 0,
        totalLatencyP95Bucket = delta?.totalLatencyP95Bucket,
    )

    private enum class ThermalStatus {
        UNAVAILABLE,
        STALE,
        NORMAL,
        HOT,
    }

    private fun thermalStatus(signal: RuntimeProotThermalSignal, nowMs: Long): ThermalStatus = when {
        signal.evidence == RuntimeProotThermalEvidence.UNAVAILABLE -> ThermalStatus.UNAVAILABLE
        signal.observedAtMs <= 0L || signal.observedAtMs > nowMs -> ThermalStatus.STALE
        nowMs - signal.observedAtMs > MAX_THERMAL_AGE_MS -> ThermalStatus.STALE
        signal.evidence == RuntimeProotThermalEvidence.TRUSTED_HOT -> ThermalStatus.HOT
        else -> ThermalStatus.NORMAL
    }

    private data class TelemetryDeltaResult(
        val delta: TelemetryDelta? = null,
        val reason: RuntimeProotAdaptiveWindowReason? = null,
    )

    private data class TelemetryDelta(
        val sampleCount: Long,
        val failureCount: Long,
        val failureRatePermille: Int,
        val totalLatencyP95Bucket: BoundedProotLatencyBucket?,
    )

    private fun telemetryDelta(
        previous: BoundedProotTaskTelemetrySnapshot,
        current: BoundedProotTaskTelemetrySnapshot,
    ): TelemetryDeltaResult {
        val previousEntries = previous.entries.associateBy { it.key }
        val currentEntries = current.entries.associateBy { it.key }
        if (
            previousEntries.size != previous.entries.size ||
            currentEntries.size != current.entries.size ||
            previous.entries.any { !it.isValidCumulativeEntry() } ||
            current.entries.any { !it.isValidCumulativeEntry() }
        ) {
            return TelemetryDeltaResult(reason = RuntimeProotAdaptiveWindowReason.TELEMETRY_INVALID)
        }

        var sampleCount = 0L
        var failureCount = 0L
        val totalBuckets = LongArray(BoundedProotLatencyBucket.entries.size)
        for ((key, currentEntry) in currentEntries) {
            val previousEntry = previousEntries[key]
            val previousCount = previousEntry?.count ?: 0L
            if (
                currentEntry.count < previousCount ||
                previousEntry != null && !currentEntry.isMonotonicFrom(previousEntry)
            ) {
                return TelemetryDeltaResult(
                    reason = RuntimeProotAdaptiveWindowReason.TELEMETRY_COUNTER_REGRESSION,
                )
            }
            val countDelta = currentEntry.count - previousCount
            sampleCount += countDelta
            if (key.result != BoundedProotTaskResultCategory.SUCCEEDED) failureCount += countDelta
            for (bucket in BoundedProotLatencyBucket.entries) {
                val currentBucket = currentEntry.total.buckets[bucket] ?: 0L
                val previousBucket = previousEntry?.total?.buckets?.get(bucket) ?: 0L
                if (currentBucket < previousBucket) {
                    return TelemetryDeltaResult(
                        reason = RuntimeProotAdaptiveWindowReason.TELEMETRY_COUNTER_REGRESSION,
                    )
                }
                totalBuckets[bucket.ordinal] += currentBucket - previousBucket
            }
        }
        if (previousEntries.keys.any { it !in currentEntries }) {
            return TelemetryDeltaResult(
                reason = RuntimeProotAdaptiveWindowReason.TELEMETRY_COUNTER_REGRESSION,
            )
        }
        if (totalBuckets.sum() != sampleCount) {
            return TelemetryDeltaResult(reason = RuntimeProotAdaptiveWindowReason.TELEMETRY_INVALID)
        }
        if (sampleCount <= 0L) {
            return TelemetryDeltaResult(
                reason = RuntimeProotAdaptiveWindowReason.TELEMETRY_SAMPLE_INSUFFICIENT,
            )
        }

        val percentileRank = (sampleCount * 95L + 99L) / 100L
        var seen = 0L
        val p95 = BoundedProotLatencyBucket.entries.firstOrNull { bucket ->
            seen += totalBuckets[bucket.ordinal]
            seen >= percentileRank
        }
        return TelemetryDeltaResult(
            delta = TelemetryDelta(
                sampleCount = sampleCount,
                failureCount = failureCount,
                failureRatePermille = ((failureCount * 1_000L) / sampleCount).toInt(),
                totalLatencyP95Bucket = p95,
            )
        )
    }

    private fun BoundedProotTelemetryEntrySnapshot.isValidCumulativeEntry(): Boolean =
        count >= 0L &&
            queue.isValidCumulativeLatency(count) &&
            execute.isValidCumulativeLatency(count) &&
            total.isValidCumulativeLatency(count)

    private fun BoundedProotTelemetryEntrySnapshot.isMonotonicFrom(
        previous: BoundedProotTelemetryEntrySnapshot,
    ): Boolean =
        queue.isMonotonicFrom(previous.queue) &&
            execute.isMonotonicFrom(previous.execute) &&
            total.isMonotonicFrom(previous.total)

    private fun BoundedProotLatencySnapshot.isMonotonicFrom(
        previous: BoundedProotLatencySnapshot,
    ): Boolean =
        sumMs >= previous.sumMs &&
            maxMs >= previous.maxMs &&
            BoundedProotLatencyBucket.entries.all {
                (buckets[it] ?: 0L) >= (previous.buckets[it] ?: 0L)
            }

    private fun BoundedProotLatencySnapshot.isValidCumulativeLatency(expectedCount: Long): Boolean =
        sumMs >= 0L &&
            maxMs >= 0L &&
            BoundedProotLatencyBucket.entries.all { (buckets[it] ?: 0L) >= 0L } &&
            BoundedProotLatencyBucket.entries.sumOf { buckets[it] ?: 0L } == expectedCount
}
