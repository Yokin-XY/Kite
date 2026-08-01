package com.kite.app.foundation.runtime

internal enum class RuntimeProotCalibrationEvidenceStatus {
    NOT_MEASURED,
    LOAD_ERROR,
    SCHEMA_MISMATCH,
    INVALID_OVERLAY,
    METHOD_MISMATCH,
    APPLIED_TIME_MISSING,
    UPPER_BOUND_NOT_MEASURED,
    TRACE_EVIDENCE_INCONSISTENT,
    READY_TRACE_GUARD_ONLY,
}

internal data class RuntimeProotProductionProfileLimits(
    val lowPower: Int,
    val balanced: Int,
    val highPerformance: Int,
)

internal data class RuntimeProotCalibrationAlignmentResult(
    val scope: String = "tracee_guard_only_no_direct_profile_selection",
    val evidenceStatus: RuntimeProotCalibrationEvidenceStatus,
    val currentProfile: RuntimeLifecyclePolicyProfileGroup,
    val currentConfiguredGlobalMax: Int,
    val productionProfileLimits: RuntimeProotProductionProfileLimits,
    val eligibleAsSafetyGuard: Boolean,
    val directProfileSelectionAllowed: Boolean = false,
    val overlayProfileLimitsIgnored: Boolean = true,
    val safeTestedMaxTracees: Int = 0,
    val throughputPeakTracees: Int = 0,
    val reason: String,
)

/** 把历史 tracee 校准证据对齐到当前 1/2/4 档，但绝不把 tracee 数直接换算为任务并发。 */
internal object RuntimeProotCalibrationAlignment {
    private const val EXPECTED_METHOD =
        "device_agnostic_single_proot_standard_task_throughput_curve_v4"

    fun align(
        overlay: RuntimeProotDeviceCalibrationOverlay,
        currentProfile: RuntimeLifecyclePolicyProfileGroup,
        lanes: List<RuntimeLanePolicy> = RuntimeWorkloadPolicy.defaultLanes(),
    ): RuntimeProotCalibrationAlignmentResult {
        val status = statusOf(overlay)
        return RuntimeProotCalibrationAlignmentResult(
            evidenceStatus = status,
            currentProfile = currentProfile,
            currentConfiguredGlobalMax = ProotPerformanceTunings.resolve(currentProfile, lanes).configuredGlobalMax,
            productionProfileLimits = productionProfileLimits(),
            eligibleAsSafetyGuard = status == RuntimeProotCalibrationEvidenceStatus.READY_TRACE_GUARD_ONLY,
            safeTestedMaxTracees = overlay.safeTestedMaxTracees.takeIf { it > 0 } ?: 0,
            throughputPeakTracees = overlay.singleProotPeakTracees.takeIf { it > 0 } ?: 0,
            reason = status.reason(),
        )
    }

    fun productionProfileLimits(): RuntimeProotProductionProfileLimits =
        RuntimeProotProductionProfileLimits(
            lowPower = ProotPerformanceTunings.resolve(
                RuntimeLifecyclePolicyProfileGroup.LOW_POWER,
                RuntimeWorkloadPolicy.defaultLanes(),
            ).configuredGlobalMax,
            balanced = ProotPerformanceTunings.resolve(
                RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
                RuntimeWorkloadPolicy.defaultLanes(),
            ).configuredGlobalMax,
            highPerformance = ProotPerformanceTunings.resolve(
                RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                RuntimeWorkloadPolicy.defaultLanes(),
            ).configuredGlobalMax,
        )

    private fun statusOf(
        overlay: RuntimeProotDeviceCalibrationOverlay,
    ): RuntimeProotCalibrationEvidenceStatus = when {
        overlay.loadError != "none" -> RuntimeProotCalibrationEvidenceStatus.LOAD_ERROR
        overlay.schema == "none" -> RuntimeProotCalibrationEvidenceStatus.NOT_MEASURED
        overlay.schema != RuntimeProotDeviceCalibrationOverlayStore.EXPECTED_SCHEMA ->
            RuntimeProotCalibrationEvidenceStatus.SCHEMA_MISMATCH
        !overlay.valid -> RuntimeProotCalibrationEvidenceStatus.INVALID_OVERLAY
        overlay.calibrationMethod != EXPECTED_METHOD -> RuntimeProotCalibrationEvidenceStatus.METHOD_MISMATCH
        overlay.appliedAtMs <= 0L -> RuntimeProotCalibrationEvidenceStatus.APPLIED_TIME_MISSING
        !overlay.upperBoundMeasured -> RuntimeProotCalibrationEvidenceStatus.UPPER_BOUND_NOT_MEASURED
        !overlay.hasConsistentTraceEvidence() ->
            RuntimeProotCalibrationEvidenceStatus.TRACE_EVIDENCE_INCONSISTENT
        else -> RuntimeProotCalibrationEvidenceStatus.READY_TRACE_GUARD_ONLY
    }

    private fun RuntimeProotDeviceCalibrationOverlay.hasConsistentTraceEvidence(): Boolean =
        safeTestedMaxTracees > 0 &&
            measuredMaxTracees > 0 &&
            singleProotPeakTracees in 1..safeTestedMaxTracees &&
            healthyStableTraceeCap in 1..safeTestedMaxTracees &&
            traceeMaxCap >= singleProotPeakTracees &&
            traceeHardCap >= traceeSoftCap &&
            singleProotQueueUntilTracees >= singleProotPeakTracees &&
            secondProotTriggerTracees > singleProotQueueUntilTracees

    private fun RuntimeProotCalibrationEvidenceStatus.reason(): String = when (this) {
        RuntimeProotCalibrationEvidenceStatus.NOT_MEASURED -> "calibration_overlay_not_measured"
        RuntimeProotCalibrationEvidenceStatus.LOAD_ERROR -> "calibration_overlay_load_error"
        RuntimeProotCalibrationEvidenceStatus.SCHEMA_MISMATCH -> "calibration_overlay_schema_mismatch"
        RuntimeProotCalibrationEvidenceStatus.INVALID_OVERLAY -> "calibration_overlay_not_valid"
        RuntimeProotCalibrationEvidenceStatus.METHOD_MISMATCH -> "calibration_method_not_current"
        RuntimeProotCalibrationEvidenceStatus.APPLIED_TIME_MISSING -> "calibration_applied_time_missing"
        RuntimeProotCalibrationEvidenceStatus.UPPER_BOUND_NOT_MEASURED ->
            "calibration_upper_bound_not_measured"
        RuntimeProotCalibrationEvidenceStatus.TRACE_EVIDENCE_INCONSISTENT ->
            "calibration_trace_evidence_inconsistent"
        RuntimeProotCalibrationEvidenceStatus.READY_TRACE_GUARD_ONLY ->
            "calibration_trace_guard_ready_profile_selection_still_blocked"
    }
}
