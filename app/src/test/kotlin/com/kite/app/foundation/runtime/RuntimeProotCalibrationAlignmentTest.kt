package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProotCalibrationAlignmentTest {
    @Test
    fun `missing overlay remains unmeasured and cannot select a profile`() {
        val result = RuntimeProotCalibrationAlignment.align(
            RuntimeProotDeviceCalibrationOverlay(),
            RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
        )

        assertEquals(RuntimeProotCalibrationEvidenceStatus.NOT_MEASURED, result.evidenceStatus)
        assertFalse(result.eligibleAsSafetyGuard)
        assertFalse(result.directProfileSelectionAllowed)
        assertEquals(2, result.currentConfiguredGlobalMax)
    }

    @Test
    fun `loader declaration gate fails closed when schema or valid is missing`() {
        assertFalse(RuntimeProotDeviceCalibrationOverlayStore.acceptsDeclaredOverlay("none", false))
        assertFalse(
            RuntimeProotDeviceCalibrationOverlayStore.acceptsDeclaredOverlay(
                RuntimeProotDeviceCalibrationOverlayStore.EXPECTED_SCHEMA,
                false,
            )
        )

        val result = RuntimeProotCalibrationAlignment.align(
            RuntimeProotDeviceCalibrationOverlay(),
            RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
        )
        assertEquals(RuntimeProotCalibrationEvidenceStatus.NOT_MEASURED, result.evidenceStatus)
        assertFalse(result.eligibleAsSafetyGuard)
    }

    @Test
    fun `wrong schema cannot become guard evidence even when valid flag is true`() {
        assertFalse(RuntimeProotDeviceCalibrationOverlayStore.acceptsDeclaredOverlay("foreign_v9", true))
        val result = RuntimeProotCalibrationAlignment.align(
            completeOverlay().copy(schema = "foreign_v9"),
            RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
        )

        assertEquals(RuntimeProotCalibrationEvidenceStatus.SCHEMA_MISMATCH, result.evidenceStatus)
        assertFalse(result.eligibleAsSafetyGuard)
    }

    @Test
    fun `current complete overlay is guard evidence but never a direct profile selector`() {
        val result = RuntimeProotCalibrationAlignment.align(
            completeOverlay().copy(
                lowPowerProfileLimit = 64,
                balancedProfileLimit = 96,
                highPerformanceProfileLimit = 128,
            ),
            RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
        )

        assertEquals(RuntimeProotCalibrationEvidenceStatus.READY_TRACE_GUARD_ONLY, result.evidenceStatus)
        assertTrue(result.eligibleAsSafetyGuard)
        assertFalse(result.directProfileSelectionAllowed)
        assertTrue(result.overlayProfileLimitsIgnored)
        assertEquals(RuntimeProotProductionProfileLimits(1, 2, 4), result.productionProfileLimits)
        assertEquals(2, result.currentConfiguredGlobalMax)
    }

    @Test
    fun `missing measured upper bound blocks adaptive evidence`() {
        val result = RuntimeProotCalibrationAlignment.align(
            completeOverlay().copy(upperBoundMeasured = false),
            RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
        )

        assertEquals(RuntimeProotCalibrationEvidenceStatus.UPPER_BOUND_NOT_MEASURED, result.evidenceStatus)
        assertFalse(result.eligibleAsSafetyGuard)
        assertEquals(4, result.currentConfiguredGlobalMax)
    }

    @Test
    fun `inconsistent trace counts fail closed`() {
        val result = RuntimeProotCalibrationAlignment.align(
            completeOverlay().copy(
                singleProotPeakTracees = 9,
                safeTestedMaxTracees = 8,
            ),
            RuntimeLifecyclePolicyProfileGroup.LOW_POWER,
        )

        assertEquals(RuntimeProotCalibrationEvidenceStatus.TRACE_EVIDENCE_INCONSISTENT, result.evidenceStatus)
        assertFalse(result.eligibleAsSafetyGuard)
    }

    @Test
    fun `production limits are derived from the sole tuning source`() {
        val limits = RuntimeProotCalibrationAlignment.productionProfileLimits()
        val lanes = RuntimeWorkloadPolicy.defaultLanes()

        assertEquals(
            ProotPerformanceTunings.resolve(RuntimeLifecyclePolicyProfileGroup.LOW_POWER, lanes).configuredGlobalMax,
            limits.lowPower,
        )
        assertEquals(
            ProotPerformanceTunings.resolve(
                RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
                lanes,
            ).configuredGlobalMax,
            limits.balanced,
        )
        assertEquals(
            ProotPerformanceTunings.resolve(
                RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                lanes,
            ).configuredGlobalMax,
            limits.highPerformance,
        )
    }

    private fun completeOverlay() = RuntimeProotDeviceCalibrationOverlay(
        schema = RuntimeProotDeviceCalibrationOverlayStore.EXPECTED_SCHEMA,
        status = "complete_ready_to_review_apply",
        source = "container_p0_runner_via_android_control_plane_bridge",
        calibrationMethod = "device_agnostic_single_proot_standard_task_throughput_curve_v4",
        valid = true,
        upperBoundMeasured = true,
        healthyStableTraceeCap = 6,
        safeTestedMaxTracees = 8,
        measuredMaxTracees = 6,
        traceeMaxCap = 6,
        traceeSoftCap = 6,
        traceeHardCap = 8,
        singleProotPeakTracees = 6,
        singleProotQueueUntilTracees = 8,
        secondProotTriggerTracees = 9,
        appliedAtMs = 10L,
    )

}
