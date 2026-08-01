package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ProotPerformanceTuningsTest {
    @Test
    fun `built in profiles keep calibrated one two and four limits`() {
        val lanes = RuntimeWorkloadPolicy.defaultLanes()

        assertTuning(RuntimeLifecyclePolicyProfileGroup.LOW_POWER, lanes, 1, 2_000L)
        assertTuning(RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED, lanes, 2, 30_000L)
        assertTuning(RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE, lanes, 4, 120_000L)
    }

    @Test
    fun `custom profile derives admission and warm limits from one bounded value`() {
        val lanes = RuntimeWorkloadPolicy.defaultLanes().map { lane ->
            if (lane.lane == RuntimeLaneKind.SERVICE) lane.copy(maxConcurrency = 3) else lane.copy(maxConcurrency = 1)
        }

        val tuning = ProotPerformanceTunings.resolve(RuntimeLifecyclePolicyProfileGroup.CUSTOM, lanes)
        val pool = WarmProotRunnerPoolTuning.forPolicy(RuntimeLifecyclePolicyProfileGroup.CUSTOM, lanes)

        assertEquals(3, tuning.configuredGlobalMax)
        assertEquals(3, tuning.maxWarmRunners)
        assertEquals(tuning.maxWarmRunners, pool.maxWarmRunners)
        assertEquals(tuning.idleTimeoutMs, pool.idleTimeoutMs)
    }

    @Test
    fun `custom profile cannot raise production ceiling above four`() {
        val lanes = RuntimeWorkloadPolicy.defaultLanes().map { it.copy(maxConcurrency = 32) }

        val tuning = ProotPerformanceTunings.resolve(RuntimeLifecyclePolicyProfileGroup.CUSTOM, lanes)

        assertEquals(4, tuning.configuredGlobalMax)
        assertEquals(4, tuning.maxWarmRunners)
    }

    private fun assertTuning(
        profile: RuntimeLifecyclePolicyProfileGroup,
        lanes: List<RuntimeLanePolicy>,
        expectedMax: Int,
        expectedIdleMs: Long,
    ) {
        val tuning = ProotPerformanceTunings.resolve(profile, lanes)
        val pool = WarmProotRunnerPoolTuning.forPolicy(profile, lanes)
        assertEquals(expectedMax, tuning.configuredGlobalMax)
        assertEquals(expectedMax, tuning.maxWarmRunners)
        assertEquals(expectedIdleMs, tuning.idleTimeoutMs)
        assertEquals(tuning.maxWarmRunners, pool.maxWarmRunners)
        assertEquals(tuning.idleTimeoutMs, pool.idleTimeoutMs)
    }
}
