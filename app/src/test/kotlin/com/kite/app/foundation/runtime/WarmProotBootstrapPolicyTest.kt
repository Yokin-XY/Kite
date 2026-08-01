package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WarmProotBootstrapPolicyTest {
    @Test
    fun `normal host memory keeps configured profile concurrency`() {
        assertEffectiveMax(RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED, RuntimePressureLevel.NORMAL, 2)
        assertEffectiveMax(RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE, RuntimePressureLevel.NORMAL, 4)
        assertEffectiveMax(RuntimeLifecyclePolicyProfileGroup.LOW_POWER, RuntimePressureLevel.NORMAL, 1)
    }

    @Test
    fun `missing or high pressure signal remains conservative`() {
        assertEffectiveMax(RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED, RuntimePressureLevel.UNKNOWN, 1)
        assertEffectiveMax(RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED, RuntimePressureLevel.HIGH, 1)
        assertEffectiveMax(RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE, RuntimePressureLevel.CRITICAL, 1)
    }

    @Test
    fun `runtime health overrides bootstrap and late bootstrap cannot overwrite it`() {
        val initial = WarmProotPolicyState(
            source = WarmProotPolicySource.INITIAL_CONSERVATIVE,
            policy = ProotJobAdmissionPolicy(),
        )
        val bootstrap = transitionWarmProotPolicy(
            current = initial,
            source = WarmProotPolicySource.BOOTSTRAP_POLICY_FILES_HOST_MEMORY,
            policy = policy(RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED, RuntimePressureLevel.NORMAL),
        )
        val formal = transitionWarmProotPolicy(
            current = bootstrap,
            source = WarmProotPolicySource.RUNTIME_HEALTH,
            policy = policy(RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE, RuntimePressureLevel.NORMAL),
        )
        val lateBootstrap = transitionWarmProotPolicy(
            current = formal,
            source = WarmProotPolicySource.BOOTSTRAP_POLICY_FILES_HOST_MEMORY,
            policy = policy(RuntimeLifecyclePolicyProfileGroup.LOW_POWER, RuntimePressureLevel.NORMAL),
        )

        assertEquals(WarmProotPolicySource.RUNTIME_HEALTH, formal.source)
        assertEquals(RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE, formal.policy.profileGroup)
        assertSame(formal, lateBootstrap)
    }

    @Test
    fun `profile inference is shared with lifecycle policy surface`() {
        assertEquals(
            RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
            inferRuntimeLifecyclePolicyProfileGroup(
                RuntimeReclaimerProfile.BALANCED,
                RuntimeResidentProfile.BALANCED,
            ),
        )
        assertEquals(
            RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
            inferRuntimeLifecyclePolicyProfileGroup(
                RuntimeReclaimerProfile.AGGRESSIVE,
                RuntimeResidentProfile.AGGRESSIVE,
            ),
        )
    }

    private fun assertEffectiveMax(
        profile: RuntimeLifecyclePolicyProfileGroup,
        pressure: RuntimePressureLevel,
        expected: Int,
    ) {
        val controller = ProotJobAdmissionController(
            WarmProotBootstrapPolicyResolver.resolve(
                profileGroup = profile,
                lanes = RuntimeWorkloadPolicy.defaultLanes(),
                pressure = pressure,
            )
        )
        controller.use {
            assertEquals(expected, controller.snapshot().effectiveGlobalMax)
        }
    }

    private fun policy(
        profile: RuntimeLifecyclePolicyProfileGroup,
        pressure: RuntimePressureLevel,
    ): ProotJobAdmissionPolicy = WarmProotBootstrapPolicyResolver.resolve(
        profileGroup = profile,
        lanes = RuntimeWorkloadPolicy.defaultLanes(),
        pressure = pressure,
    )
}
