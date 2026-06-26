package com.kftest.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePressureStabilityGateDryRunTest {
    @Test
    fun evaluate_requiresThreeStableRefreshesBeforeArming() {
        RuntimePressureStabilityGateDryRun.evaluate(
            pressureConsumer = pressureConsumer(
                generatedAtMs = 1L,
                state = RuntimePressureConsumerState.BUSY,
                prootPressureScore = 60
            ),
            budgetPressure = budgetPressure(generatedAtMs = 1L),
            now = 1L
        )

        val first = RuntimePressureStabilityGateDryRun.evaluate(
            pressureConsumer = pressureConsumer(generatedAtMs = 10L),
            budgetPressure = budgetPressure(generatedAtMs = 10L),
            now = 10L
        )
        val second = RuntimePressureStabilityGateDryRun.evaluate(
            pressureConsumer = pressureConsumer(generatedAtMs = 20L),
            budgetPressure = budgetPressure(generatedAtMs = 20L),
            now = 20L
        )
        val third = RuntimePressureStabilityGateDryRun.evaluate(
            pressureConsumer = pressureConsumer(generatedAtMs = 30L),
            budgetPressure = budgetPressure(generatedAtMs = 30L),
            now = 30L
        )

        assertEquals(RuntimePressureCanaryArmingState.WARMING, first.canaryArmingState)
        assertFalse(first.canaryStable)
        assertEquals(RuntimePressureCanaryArmingState.WARMING, second.canaryArmingState)
        assertEquals(RuntimePressureCanaryArmingState.ARMED, third.canaryArmingState)
        assertTrue(third.canaryStable)
        assertEquals(
            RuntimePressureStabilityRecommendation.READY_FOR_PRESSURE_CANARY,
            third.recommendation
        )
    }

    private fun pressureConsumer(
        generatedAtMs: Long,
        state: RuntimePressureConsumerState = RuntimePressureConsumerState.QUIET,
        prootPressureScore: Int = 0
    ): RuntimePressureConsumerSnapshot {
        return RuntimePressureConsumerSnapshot(
            generatedAtMs = generatedAtMs,
            state = state,
            recommendation = RuntimePressureConsumerRecommendation.OBSERVE_ONLY,
            telemetryHealthy = true,
            telemetryStatus = "loaded",
            prootSignalLevel = if (state == RuntimePressureConsumerState.BUSY) {
                ProotPressureSignalLevel.BUSY
            } else {
                ProotPressureSignalLevel.QUIET
            },
            prootPressureScore = prootPressureScore,
            eventsInWindow = 1,
            rssPressureLevel = RuntimePressureLevel.NORMAL
        )
    }

    private fun budgetPressure(generatedAtMs: Long): RuntimeBudgetPressureDryRunSnapshot {
        return RuntimeBudgetPressureDryRunSnapshot(
            generatedAtMs = generatedAtMs,
            overallState = RuntimeBudgetState.HEALTHY
        )
    }
}
