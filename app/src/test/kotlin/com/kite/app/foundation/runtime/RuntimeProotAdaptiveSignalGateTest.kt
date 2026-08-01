package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RuntimeProotAdaptiveSignalGateTest {
    @Test
    fun `high memory pressure immediately suggests one step downgrade without telemetry`() {
        val decision = evaluate(
            tuning = tuning(
                profile = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                pressure = RuntimePressureLevel.HIGH,
            ),
            previous = snapshot(),
            current = snapshot(),
        )

        assertEquals(RuntimeProotAdaptiveWindowAction.DOWNGRADE_ONE, decision.action)
        assertEquals(RuntimeProotAdaptiveWindowReason.MEMORY_PRESSURE_HIGH, decision.reason)
        assertEquals(4, decision.currentConfiguredGlobalMax)
        assertEquals(2, decision.suggestedConfiguredGlobalMax)
        assertFalse(decision.changesCoordinator)
    }

    @Test
    fun `significant completed task failure rate suggests one step downgrade`() {
        val decision = evaluate(
            previous = snapshot(successes = 10),
            current = snapshot(successes = 18, failures = 2),
        )

        assertEquals(RuntimeProotAdaptiveWindowAction.DOWNGRADE_ONE, decision.action)
        assertEquals(RuntimeProotAdaptiveWindowReason.FAILURE_RATE_HIGH, decision.reason)
        assertEquals(10L, decision.sampleCount)
        assertEquals(2L, decision.failureCount)
        assertEquals(200, decision.failureRatePermille)
        assertEquals(1, decision.suggestedConfiguredGlobalMax)
    }

    @Test
    fun `missing thermal evidence blocks otherwise healthy promotion window`() {
        val decision = evaluate(
            previous = snapshot(successes = 10),
            current = snapshot(successes = 30),
        )

        assertEquals(RuntimeProotAdaptiveWindowAction.HOLD, decision.action)
        assertEquals(RuntimeProotAdaptiveWindowReason.THERMAL_SIGNAL_UNAVAILABLE, decision.reason)
        assertEquals(2, decision.suggestedConfiguredGlobalMax)
    }

    @Test
    fun `trusted healthy window only becomes one step promotion candidate`() {
        val decision = evaluate(
            previous = snapshot(successes = 10),
            current = snapshot(successes = 35),
            thermal = RuntimeProotThermalSignal(
                evidence = RuntimeProotThermalEvidence.TRUSTED_NORMAL,
                observedAtMs = NOW_MS - 5_000L,
            ),
        )

        assertEquals(RuntimeProotAdaptiveWindowAction.PROMOTION_WINDOW_ELIGIBLE, decision.action)
        assertEquals(RuntimeProotAdaptiveWindowReason.HEALTHY_WINDOW, decision.reason)
        assertEquals(2, decision.currentConfiguredGlobalMax)
        assertEquals(4, decision.suggestedConfiguredGlobalMax)
        assertEquals(BoundedProotLatencyBucket.LE_100_MS, decision.totalLatencyP95Bucket)
        assertFalse(decision.changesCoordinator)
    }

    @Test
    fun `single good sample cannot become promotion candidate`() {
        val decision = evaluate(
            current = snapshot(successes = 1),
            thermal = RuntimeProotThermalSignal(
                evidence = RuntimeProotThermalEvidence.TRUSTED_NORMAL,
                observedAtMs = NOW_MS,
            ),
        )

        assertEquals(RuntimeProotAdaptiveWindowAction.HOLD, decision.action)
        assertEquals(RuntimeProotAdaptiveWindowReason.TELEMETRY_SAMPLE_INSUFFICIENT, decision.reason)
    }

    @Test
    fun `stale thermal evidence cannot authorize promotion`() {
        val decision = evaluate(
            current = snapshot(successes = 20),
            thermal = RuntimeProotThermalSignal(
                evidence = RuntimeProotThermalEvidence.TRUSTED_NORMAL,
                observedAtMs = NOW_MS - RuntimeProotAdaptiveSignalGate.MAX_THERMAL_AGE_MS - 1L,
            ),
        )

        assertEquals(RuntimeProotAdaptiveWindowReason.THERMAL_SIGNAL_STALE, decision.reason)
        assertEquals(RuntimeProotAdaptiveWindowAction.HOLD, decision.action)
    }

    @Test
    fun `stale task window and counter regression fail closed`() {
        val stale = evaluate(
            nowMs = NOW_MS + RuntimeProotAdaptiveSignalGate.MAX_WINDOW_AGE_MS + 1L,
            current = snapshot(successes = 20),
        )
        assertEquals(RuntimeProotAdaptiveWindowReason.WINDOW_STALE, stale.reason)

        val regressed = evaluate(
            previous = snapshot(successes = 20),
            current = snapshot(successes = 19),
        )
        assertEquals(RuntimeProotAdaptiveWindowReason.TELEMETRY_COUNTER_REGRESSION, regressed.reason)
        assertEquals(RuntimeProotAdaptiveWindowAction.HOLD, regressed.action)
    }

    @Test
    fun `bootstrap policy and custom profile cannot drive adaptive decisions`() {
        val bootstrap = evaluate(
            tuning = tuning(source = WarmProotPolicySource.BOOTSTRAP_POLICY_FILES_HOST_MEMORY),
            current = snapshot(successes = 20),
        )
        assertEquals(RuntimeProotAdaptiveWindowReason.POLICY_SOURCE_NOT_ACTUAL, bootstrap.reason)

        val custom = evaluate(
            tuning = tuning(profile = RuntimeLifecyclePolicyProfileGroup.CUSTOM),
            current = snapshot(successes = 20),
        )
        assertEquals(RuntimeProotAdaptiveWindowReason.PROFILE_NOT_ADAPTIVE, custom.reason)
    }

    @Test
    fun `trusted hot thermal evidence suggests downgrade but lowest profile is held`() {
        val hot = RuntimeProotThermalSignal(
            evidence = RuntimeProotThermalEvidence.TRUSTED_HOT,
            observedAtMs = NOW_MS,
        )
        val downgrade = evaluate(thermal = hot)
        assertEquals(RuntimeProotAdaptiveWindowReason.THERMAL_PRESSURE_HIGH, downgrade.reason)
        assertEquals(RuntimeProotAdaptiveWindowAction.DOWNGRADE_ONE, downgrade.action)

        val lowest = evaluate(
            tuning = tuning(profile = RuntimeLifecyclePolicyProfileGroup.LOW_POWER),
            thermal = hot,
        )
        assertEquals(RuntimeProotAdaptiveWindowReason.THERMAL_PRESSURE_HIGH, lowest.reason)
        assertEquals(RuntimeProotAdaptiveWindowAction.HOLD, lowest.action)
        assertEquals(1, lowest.suggestedConfiguredGlobalMax)
    }

    @Test
    fun `high latency blocks promotion and highest profile never promotes past four`() {
        val thermal = RuntimeProotThermalSignal(
            evidence = RuntimeProotThermalEvidence.TRUSTED_NORMAL,
            observedAtMs = NOW_MS,
        )
        val slow = evaluate(
            current = snapshot(successes = 20, latency = BoundedProotLatencyBucket.LE_5000_MS),
            thermal = thermal,
        )
        assertEquals(RuntimeProotAdaptiveWindowReason.LATENCY_TOO_HIGH, slow.reason)

        val highest = evaluate(
            tuning = tuning(profile = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE),
            current = snapshot(successes = 20),
            thermal = thermal,
        )
        assertEquals(RuntimeProotAdaptiveWindowReason.ALREADY_HIGHEST, highest.reason)
        assertEquals(RuntimeProotAdaptiveWindowAction.HOLD, highest.action)
        assertEquals(4, highest.suggestedConfiguredGlobalMax)
    }

    private fun evaluate(
        nowMs: Long = NOW_MS,
        tuning: WarmProotExecutionCoordinator.TuningSnapshot = tuning(),
        previous: BoundedProotTaskTelemetrySnapshot = snapshot(),
        current: BoundedProotTaskTelemetrySnapshot = snapshot(),
        thermal: RuntimeProotThermalSignal = RuntimeProotThermalSignal(),
    ): RuntimeProotAdaptiveWindowDecision = RuntimeProotAdaptiveSignalGate.evaluate(
        RuntimeProotAdaptiveSignalInput(
            nowMs = nowMs,
            tuning = tuning,
            calibration = calibration(tuning.profileGroup, tuning.configuredGlobalMax),
            previousTelemetry = RuntimeProotTelemetryReading(
                capturedAtMs = WINDOW_START_MS,
                snapshot = previous,
            ),
            currentTelemetry = RuntimeProotTelemetryReading(
                capturedAtMs = WINDOW_END_MS,
                snapshot = current,
            ),
            thermal = thermal,
        )
    )

    private fun tuning(
        source: WarmProotPolicySource = WarmProotPolicySource.RUNTIME_HEALTH,
        profile: RuntimeLifecyclePolicyProfileGroup = RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
        pressure: RuntimePressureLevel = RuntimePressureLevel.NORMAL,
    ): WarmProotExecutionCoordinator.TuningSnapshot {
        val max = when (profile) {
            RuntimeLifecyclePolicyProfileGroup.LOW_POWER -> 1
            RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED -> 2
            RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE -> 4
            RuntimeLifecyclePolicyProfileGroup.CUSTOM -> 2
        }
        return WarmProotExecutionCoordinator.TuningSnapshot(
            policySource = source,
            profileGroup = profile,
            pressure = pressure,
            foreground = true,
            configuredGlobalMax = max,
            effectiveGlobalMax = max,
            maxWarmRunners = max,
            idleTimeoutMs = 30_000L,
            activeJobs = 0,
            queuedJobs = 0,
            totalWarmSessions = 0,
            activeWarmSessions = 0,
            idleWarmSessions = 0,
            staleWarmSessions = 0,
            oldestIdleAgeMs = 0L,
        )
    }

    private fun calibration(
        profile: RuntimeLifecyclePolicyProfileGroup,
        currentMax: Int,
    ) = RuntimeProotCalibrationAlignmentResult(
        evidenceStatus = RuntimeProotCalibrationEvidenceStatus.READY_TRACE_GUARD_ONLY,
        currentProfile = profile,
        currentConfiguredGlobalMax = currentMax,
        productionProfileLimits = RuntimeProotProductionProfileLimits(1, 2, 4),
        eligibleAsSafetyGuard = true,
        safeTestedMaxTracees = 4,
        throughputPeakTracees = 2,
        reason = "test_guard_ready",
    )

    private fun snapshot(
        successes: Long = 0L,
        failures: Long = 0L,
        latency: BoundedProotLatencyBucket = BoundedProotLatencyBucket.LE_100_MS,
    ): BoundedProotTaskTelemetrySnapshot {
        val entries = buildList {
            if (successes > 0L) {
                add(entry(BoundedProotTaskResultCategory.SUCCEEDED, successes, latency))
            }
            if (failures > 0L) {
                add(entry(BoundedProotTaskResultCategory.EXECUTION_FAILED, failures, latency))
            }
        }
        return BoundedProotTaskTelemetrySnapshot(entries)
    }

    private fun entry(
        result: BoundedProotTaskResultCategory,
        count: Long,
        latency: BoundedProotLatencyBucket,
    ): BoundedProotTelemetryEntrySnapshot {
        val latencySnapshot = BoundedProotLatencySnapshot(
            sumMs = count * 100L,
            maxMs = if (count > 0L) 100L else 0L,
            buckets = BoundedProotLatencyBucket.entries.associateWith {
                if (it == latency) count else 0L
            },
        )
        return BoundedProotTelemetryEntrySnapshot(
            key = BoundedProotTelemetryKey(
                lane = RuntimeLaneKind.PROBE,
                route = WarmProotExecutionRoute.WARM_RUNNER,
                result = result,
            ),
            count = count,
            queue = latencySnapshot,
            execute = latencySnapshot,
            total = latencySnapshot,
        )
    }

    companion object {
        private const val WINDOW_START_MS = 1_000_000L
        private const val WINDOW_END_MS = WINDOW_START_MS + 60_000L
        private const val NOW_MS = WINDOW_END_MS + 5_000L
    }
}
