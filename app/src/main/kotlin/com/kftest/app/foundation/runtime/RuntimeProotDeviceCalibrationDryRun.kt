package com.kftest.app.foundation.runtime

import java.io.File
import org.json.JSONObject

enum class RuntimeProotDeviceCalibrationState {
    BLOCKED,
    SAFE_BOOTSTRAP,
    PLAN_READY,
    RESULT_OVERLAY_READY
}

enum class RuntimeProotDeviceCalibrationRecommendation {
    WAIT_FOR_SAFE_BASELINE,
    USE_SAFE_BOOTSTRAP,
    RUN_P0_CALIBRATION,
    APPLY_LOCAL_OVERLAY
}

data class RuntimeProotDeviceCalibrationOverlay(
    val path: String = "/workspace/.kf/proot-device-calibration.json",
    val status: String = "not_measured_safe_defaults_active",
    val source: String = "safe_defaults",
    val calibrationMethod: String = "none",
    val valid: Boolean = false,
    val upperBoundMeasured: Boolean = false,
    val healthyStableTraceeCap: Int = 8,
    val budgetKneeTracees: Int = 0,
    val budgetKneeUsedForCapacity: Boolean = false,
    val budgetKneePolicy: String = "advisory_budget_observation_not_capacity_trigger",
    val modelGuardKneeTracees: Int = 0,
    val safeTestedMaxTracees: Int = 0,
    val measuredMaxTracees: Int = 0,
    val defaultStartCap: Int = RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP,
    val traceeMaxCap: Int = 16,
    val traceeSoftCap: Int = 8,
    val traceeHardCap: Int = 16,
    val memoryWorkerRssKb: Long = 96L * 1024L,
    val singleProotPeakTracees: Int = RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP,
    val singleProotQueueUntilTracees: Int = RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP,
    val secondProotTriggerTracees: Int =
        RuntimeProotDeviceCalibrationDefaults.scaleOutThreshold(
            RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP
        ),
    val overflowHeadroomTracees: Int = 1,
    val singleProotOverflowPercent: Int =
        RuntimeProotDeviceCalibrationDefaults.SINGLE_PROOT_OVERFLOW_PERCENT,
    val queueHeadroomPercent: Int = RuntimeProotDeviceCalibrationDefaults.SINGLE_PROOT_OVERFLOW_PERCENT,
    val secondProotTriggerHeadroomPercent: Int =
        RuntimeProotDeviceCalibrationDefaults.SINGLE_PROOT_OVERFLOW_PERCENT,
    val overflowPercentBase: String = RuntimeProotDeviceCalibrationDefaults.OVERFLOW_PERCENT_BASE,
    val queueStrategyPercentBase: String = RuntimeProotDeviceCalibrationDefaults.OVERFLOW_PERCENT_BASE,
    val queueStrategyPolicy: String = RuntimeProotDeviceCalibrationDefaults.QUEUE_STRATEGY_POLICY,
    val lowPowerProfileLimit: Int = 1,
    val balancedProfileLimit: Int = 1,
    val highPerformanceProfileLimit: Int = 2,
    val profileLimitPolicy: String = RuntimeProotDeviceCalibrationDefaults.PROFILE_LIMIT_POLICY,
    val loadError: String = "none"
)

private object RuntimeProotDeviceCalibrationDefaults {
    const val DEFAULT_START_CAP = 12
    const val SINGLE_PROOT_OVERFLOW_PERCENT = 25
    const val OVERFLOW_PERCENT_BASE = "single_proot_peak_multiplier"
    const val QUEUE_HEADROOM_PERCENT = SINGLE_PROOT_OVERFLOW_PERCENT
    const val SECOND_PROOT_TRIGGER_HEADROOM_PERCENT = SINGLE_PROOT_OVERFLOW_PERCENT
    const val QUEUE_STRATEGY_PERCENT_BASE = OVERFLOW_PERCENT_BASE
    const val QUEUE_STRATEGY_POLICY =
        "single_proot_peak_multiplier_then_next_proot_v1"
    const val PROFILE_LIMIT_POLICY =
        "deprecated_profile_bands_replaced_by_single_peak_multiplier_v1"

    @Suppress("UNUSED_PARAMETER")
    fun deriveQueueStrategy(
        measuredMaxTracees: Int,
        traceeHardCap: Int,
        singleProotOverflowPercent: Int = SINGLE_PROOT_OVERFLOW_PERCENT
    ): IntArray {
        val peak = measuredMaxTracees.coerceAtLeast(1)
        val trigger = scaleOutThreshold(peak, singleProotOverflowPercent)
        return intArrayOf(peak, peak, trigger, trigger - peak)
    }

    fun scaleOutThreshold(
        peak: Int,
        percent: Int = SINGLE_PROOT_OVERFLOW_PERCENT
    ): Int {
        val safePercent = percent.coerceAtLeast(0)
        val safePeak = peak.coerceAtLeast(1)
        return (((safePeak * (100 + safePercent)) + 99) / 100)
            .coerceAtLeast(safePeak + 1)
    }
}

data class RuntimeProotDeviceCalibrationResetResult(
    val action: String = "RESET_PROOT_DEVICE_CALIBRATION",
    val workspacePath: String?,
    val overlayPath: String,
    val archivePath: String = "none",
    val resetApplied: Boolean = false,
    val alreadyDefault: Boolean = false,
    val reason: String,
    val error: String = "none"
) {
    fun summary(): String {
        return "action=$action resetApplied=$resetApplied alreadyDefault=$alreadyDefault " +
            "overlay=$overlayPath archive=$archivePath error=$error"
    }

    fun toLogBlock(): String {
        return buildString {
            appendLine("== reset-proot-device-calibration ==")
            appendLine("action=$action")
            appendLine("workspacePath=${workspacePath ?: "none"}")
            appendLine("overlayPath=$overlayPath")
            appendLine("archivePath=$archivePath")
            appendLine("resetApplied=$resetApplied")
            appendLine("alreadyDefault=$alreadyDefault")
            appendLine("reason=$reason")
            appendLine("error=$error")
            appendLine("expectedNextState=safe_defaults_active_until_p0_calibration_overlay_is_written")
        }
    }
}

object RuntimeProotDeviceCalibrationOverlayStore {
    private const val RELATIVE_OVERLAY_PATH = ".kf/proot-device-calibration.json"

    fun load(workspacePath: String?): RuntimeProotDeviceCalibrationOverlay {
        if (workspacePath.isNullOrBlank()) {
            return RuntimeProotDeviceCalibrationOverlay(
                status = "workspace_not_ready_safe_defaults_active",
                loadError = "workspace_path_missing"
            )
        }
        val file = File(workspacePath, RELATIVE_OVERLAY_PATH)
        if (!file.exists()) {
            return RuntimeProotDeviceCalibrationOverlay(
                path = file.absolutePath,
                status = "not_measured_safe_defaults_active"
            )
        }
        return runCatching {
            val json = JSONObject(file.readText())
            val queueStrategy = json.optJSONObject("queueStrategy")
                ?: json.optJSONObject("strategy")
            val profileLimits = json.optJSONObject("profileLimits")
            val measuredMaxTracees = json.optPositiveInt(
                "measuredMaxTracees",
                json.optPositiveInt("traceeMaxCap", json.optPositiveInt("traceeHardCap", 16))
            )
            val traceeMaxCap = json.optPositiveInt("traceeMaxCap", measuredMaxTracees)
            val healthyStableTraceeCap = json.optPositiveInt(
                "healthyStableTraceeCap",
                json.optPositiveInt(
                    "comfortTracees",
                    json.optPositiveInt("traceeSoftCap", RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP)
                )
            ).coerceIn(1, traceeMaxCap.coerceAtLeast(1))
            val traceeSoftCap = json.optPositiveInt(
                "traceeSoftCap",
                json.optPositiveInt("tracee_soft_cap", traceeMaxCap)
            ).coerceIn(1, traceeMaxCap.coerceAtLeast(1))
            val traceeHardCap = json.optPositiveInt(
                "traceeHardCap",
                json.optPositiveInt("tracee_hard_cap", traceeMaxCap)
            ).coerceAtLeast(traceeSoftCap.coerceAtLeast(1) + 1)
            val memoryWorkerRssKb = json.optPositiveLong(
                "memoryWorkerRssKb",
                json.optPositiveLong("memory_worker_rss_kb", 96L * 1024L)
            )
            val defaultStartCap = json.optPositiveInt(
                "defaultStartCap",
                RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP
            ).coerceIn(1, traceeHardCap)
            val singleProotOverflowPercent = queueStrategy?.optPositiveInt(
                "singleProotOverflowPercent",
                RuntimeProotDeviceCalibrationDefaults.SINGLE_PROOT_OVERFLOW_PERCENT
            ) ?: json.optPositiveInt(
                "singleProotOverflowPercent",
                RuntimeProotDeviceCalibrationDefaults.SINGLE_PROOT_OVERFLOW_PERCENT
            )
            val derivedStrategy = RuntimeProotDeviceCalibrationDefaults.deriveQueueStrategy(
                measuredMaxTracees = traceeMaxCap,
                traceeHardCap = traceeHardCap,
                singleProotOverflowPercent = singleProotOverflowPercent
            )
            val benchmarkResult = json.optJSONObject("benchmarkResult")
            val singleProotPeakTracees = queueStrategy?.optPositiveInt(
                "throughputPeakTracees",
                benchmarkResult?.optPositiveInt("peakTasks", derivedStrategy[0]) ?: derivedStrategy[0]
            ) ?: json.optPositiveInt(
                "singleProotPeakTracees",
                json.optPositiveInt(
                    "peakTasks",
                    json.optPositiveInt(
                        "throughputBestTracees",
                        benchmarkResult?.optPositiveInt("peakTasks", derivedStrategy[0]) ?: derivedStrategy[0]
                    )
                )
            )
            val safeSingleProotPeakTracees = singleProotPeakTracees.coerceIn(1, traceeHardCap)
            val percentDerivedStrategy = RuntimeProotDeviceCalibrationDefaults.deriveQueueStrategy(
                measuredMaxTracees = safeSingleProotPeakTracees,
                traceeHardCap = traceeHardCap,
                singleProotOverflowPercent = singleProotOverflowPercent
            )
            val rawSingleProotLimitTracees = queueStrategy?.optPositiveInt(
                "singleProotLimitTracees",
                json.optPositiveInt("singleProotLimitTracees", percentDerivedStrategy[1])
            ) ?: json.optPositiveInt("singleProotLimitTracees", percentDerivedStrategy[1])
            val safeSingleProotQueueUntilTracees = rawSingleProotLimitTracees
                .coerceIn(safeSingleProotPeakTracees, percentDerivedStrategy[2] - 1)
            val safeSecondProotTriggerTracees = percentDerivedStrategy[2]
            val safeOverflowHeadroomTracees = percentDerivedStrategy[3].coerceAtLeast(0)
            RuntimeProotDeviceCalibrationOverlay(
                path = file.absolutePath,
                status = json.optString("status", "loaded"),
                source = json.optString("source", "local_device_calibration_overlay"),
                calibrationMethod = json.optString("calibrationMethod", "unknown"),
                valid = json.optBoolean("valid", true),
                upperBoundMeasured = json.optBoolean("upperBoundMeasured", false),
                healthyStableTraceeCap = healthyStableTraceeCap,
                budgetKneeTracees = json.optPositiveInt("budgetKneeTracees", 0),
                budgetKneeUsedForCapacity = false,
                budgetKneePolicy = json.optString(
                    "budgetKneePolicy",
                    "advisory_budget_observation_not_capacity_trigger"
                ),
                modelGuardKneeTracees = json.optPositiveInt("modelGuardKneeTracees", 0),
                safeTestedMaxTracees = json.optPositiveInt("safeTestedMaxTracees", measuredMaxTracees),
                measuredMaxTracees = measuredMaxTracees,
                defaultStartCap = defaultStartCap,
                traceeMaxCap = traceeMaxCap.coerceAtLeast(traceeSoftCap.coerceAtLeast(1)),
                traceeSoftCap = traceeSoftCap.coerceAtLeast(1),
                traceeHardCap = traceeHardCap.coerceAtLeast(traceeSoftCap.coerceAtLeast(1) + 1),
                memoryWorkerRssKb = memoryWorkerRssKb.coerceAtLeast(16L * 1024L),
                singleProotPeakTracees = safeSingleProotPeakTracees,
                singleProotQueueUntilTracees = safeSingleProotQueueUntilTracees,
                secondProotTriggerTracees = safeSecondProotTriggerTracees,
                overflowHeadroomTracees = safeOverflowHeadroomTracees,
                singleProotOverflowPercent = singleProotOverflowPercent,
                queueHeadroomPercent = singleProotOverflowPercent,
                secondProotTriggerHeadroomPercent = singleProotOverflowPercent,
                overflowPercentBase = queueStrategy?.optString(
                    "percentBase",
                    RuntimeProotDeviceCalibrationDefaults.OVERFLOW_PERCENT_BASE
                ) ?: json.optString(
                    "overflowPercentBase",
                    RuntimeProotDeviceCalibrationDefaults.OVERFLOW_PERCENT_BASE
                ),
                queueStrategyPercentBase = queueStrategy?.optString(
                    "percentBase",
                    RuntimeProotDeviceCalibrationDefaults.OVERFLOW_PERCENT_BASE
                ) ?: json.optString(
                    "queueStrategyPercentBase",
                    RuntimeProotDeviceCalibrationDefaults.OVERFLOW_PERCENT_BASE
                ),
                queueStrategyPolicy = RuntimeProotDeviceCalibrationDefaults.QUEUE_STRATEGY_POLICY,
                lowPowerProfileLimit = profileLimits?.optPositiveInt("lowPower", safeSingleProotPeakTracees)
                    ?: safeSingleProotPeakTracees,
                balancedProfileLimit = profileLimits?.optPositiveInt("balanced", safeSingleProotQueueUntilTracees)
                    ?: safeSingleProotQueueUntilTracees,
                highPerformanceProfileLimit = profileLimits?.optPositiveInt(
                    "highPerformance",
                    safeSecondProotTriggerTracees
                ) ?: safeSecondProotTriggerTracees,
                profileLimitPolicy = json.optString(
                    "profileLimitPolicy",
                    RuntimeProotDeviceCalibrationDefaults.PROFILE_LIMIT_POLICY
                )
            )
        }.getOrElse { error ->
            RuntimeProotDeviceCalibrationOverlay(
                path = file.absolutePath,
                status = "invalid_overlay_safe_defaults_active",
                loadError = error.javaClass.simpleName
            )
        }
    }

    fun reset(
        workspacePath: String?,
        reason: String,
        now: Long = System.currentTimeMillis()
    ): RuntimeProotDeviceCalibrationResetResult {
        if (workspacePath.isNullOrBlank()) {
            return RuntimeProotDeviceCalibrationResetResult(
                workspacePath = workspacePath,
                overlayPath = RELATIVE_OVERLAY_PATH,
                reason = reason,
                error = "workspace_path_missing"
            )
        }
        val file = File(workspacePath, RELATIVE_OVERLAY_PATH)
        if (!file.exists()) {
            return RuntimeProotDeviceCalibrationResetResult(
                workspacePath = workspacePath,
                overlayPath = file.absolutePath,
                resetApplied = true,
                alreadyDefault = true,
                reason = reason
            )
        }
        val archive = nextArchiveFile(file.parentFile ?: File(workspacePath), now)
        val moved = file.renameTo(archive)
        return RuntimeProotDeviceCalibrationResetResult(
            workspacePath = workspacePath,
            overlayPath = file.absolutePath,
            archivePath = if (moved) archive.absolutePath else "none",
            resetApplied = moved,
            alreadyDefault = false,
            reason = reason,
            error = if (moved) "none" else "archive_rename_failed"
        )
    }

    private fun nextArchiveFile(parent: File, now: Long): File {
        var index = 0
        while (true) {
            val suffix = if (index == 0) "$now" else "$now.$index"
            val candidate = File(parent, "proot-device-calibration.json.reset.$suffix")
            if (!candidate.exists()) return candidate
            index += 1
        }
    }

    private fun JSONObject.optPositiveInt(name: String, fallback: Int): Int {
        val value = optInt(name, fallback)
        return if (value > 0) value else fallback
    }

    private fun JSONObject.optPositiveLong(name: String, fallback: Long): Long {
        val value = optLong(name, fallback)
        return if (value > 0L) value else fallback
    }
}

data class RuntimeProotDeviceCalibrationDryRunSnapshot(
    val mode: String = "proot_device_calibration_p0_observe_v0",
    val enforcementMode: String = "observe_only_android_control_plane",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeProotDeviceCalibrationState = RuntimeProotDeviceCalibrationState.SAFE_BOOTSTRAP,
    val recommendation: RuntimeProotDeviceCalibrationRecommendation =
        RuntimeProotDeviceCalibrationRecommendation.USE_SAFE_BOOTSTRAP,
    val selectedGoal: String = "proot_management_p0",
    val scope: String = "single_proot_standard_task_throughput_peak_and_next_proot_trigger",
    val androidExecutionOwner: Boolean = true,
    val ubuntuWorkloadGeneratorOnly: Boolean = true,
    val adbRequired: Boolean = false,
    val rootRequired: Boolean = false,
    val productSignalContract: String =
        "normal_app_permissions_same_uid_standard_workers_proot_telemetry_memory_budget",
    val labSignalContract: String =
        "adb_optional_only_for_install_start_and_extra_diagnostics_not_required_for_p0_model",
    val safeBootstrapProfile: String = "LOW_POWER_SAFE_BOOTSTRAP",
    val safeBootstrapSingleProotParallel: Int = 1,
    val safeBootstrapGlobalProotMax: Int = 1,
    val safeBootstrapLowPriorityBackgroundAllowed: Boolean = false,
    val planProtocol: String = "plan_declared_before_step_recoverable_jsonl_v0",
    val planLogPath: String = "/workspace/.kf/proot-device-calibration-plan.jsonl",
    val resultOverlayPath: String = "/workspace/.kf/proot-device-calibration.json",
    val crashRecoveryKey: String = "read_last_PLAN_DECLARED_before_next_step",
    val p0TestCount: Int = 1,
    val p0StepSequence: String = "standard_task_workers_1_to_peak_plus_10_confirm_rounds",
    val baselineTestEnabled: Boolean = true,
    val traceeCapTestEnabled: Boolean = true,
    val memoryCostTestEnabled: Boolean = false,
    val thermalUiGateEnabled: Boolean = false,
    val cpuCostDeferred: Boolean = true,
    val ioCostDeferred: Boolean = true,
    val mixedWorkloadDeferred: Boolean = true,
    val stopCondition: String =
        "runtime_failure_or_throughput_peak_declines_for_10_confirm_rounds_or_configured_max",
    val currentLifecycleState: RuntimeAppVisibilityState = RuntimeAppVisibilityState.FOREGROUND,
    val currentBudgetState: RuntimeBudgetState = RuntimeBudgetState.HEALTHY,
    val currentTelemetryHealthy: Boolean = false,
    val currentLiveTracees: Int = 0,
    val currentForkExecWindow: Int = 0,
    val currentMemorySignal: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val currentRiskPercent: Int = 0,
    val currentBottleneckAxis: String = "none",
    val currentDecision: String = "KEEP_HEADROOM_OBSERVE",
    val baselineSatisfied: Boolean = false,
    val canRunNow: Boolean = false,
    val blocker: String = "waiting_for_runtime",
    val conservativeTraceeSoftCap: Int = 8,
    val conservativeTraceeHardCap: Int = 16,
    val conservativeMemoryWorkerRssKb: Long = 96L * 1024L,
    val conservativeSingleProotBalancedParallel: Int = 1,
    val overlaySchema: String = "proot_device_calibration_v0",
    val overlaySource: String = "product_no_adb_calibration",
    val overlayStatus: String = "not_measured_safe_defaults_active",
    val overlayValid: Boolean = false,
    val overlayLoadError: String = "none",
    val overlayMethod: String = "none",
    val overlayUpperBoundMeasured: Boolean = false,
    val overlayHealthyStableTraceeCap: Int = 8,
    val overlayBudgetKneeTracees: Int = 0,
    val overlayBudgetKneeUsedForCapacity: Boolean = false,
    val overlayBudgetKneePolicy: String = "advisory_budget_observation_not_capacity_trigger",
    val overlayModelGuardKneeTracees: Int = 0,
    val overlaySafeTestedMaxTracees: Int = 0,
    val overlayMeasuredMaxTracees: Int = 0,
    val overlayDefaultStartCap: Int = RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP,
    val overlayTraceeMaxCap: Int = 16,
    val singleProotPeakTracees: Int = RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP,
    val singleProotQueueUntilTracees: Int = RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP,
    val secondProotTriggerTracees: Int =
        RuntimeProotDeviceCalibrationDefaults.scaleOutThreshold(
            RuntimeProotDeviceCalibrationDefaults.DEFAULT_START_CAP
        ),
    val overflowHeadroomTracees: Int = 1,
    val singleProotOverflowPercent: Int =
        RuntimeProotDeviceCalibrationDefaults.SINGLE_PROOT_OVERFLOW_PERCENT,
    val queueHeadroomPercent: Int = RuntimeProotDeviceCalibrationDefaults.SINGLE_PROOT_OVERFLOW_PERCENT,
    val secondProotTriggerHeadroomPercent: Int =
        RuntimeProotDeviceCalibrationDefaults.SINGLE_PROOT_OVERFLOW_PERCENT,
    val overflowPercentBase: String = RuntimeProotDeviceCalibrationDefaults.OVERFLOW_PERCENT_BASE,
    val queueStrategyPercentBase: String = RuntimeProotDeviceCalibrationDefaults.OVERFLOW_PERCENT_BASE,
    val queueStrategyPolicy: String = RuntimeProotDeviceCalibrationDefaults.QUEUE_STRATEGY_POLICY,
    val lowPowerProfileLimit: Int = 1,
    val balancedProfileLimit: Int = 2,
    val highPerformanceProfileLimit: Int = 4,
    val profileLimitPolicy: String = RuntimeProotDeviceCalibrationDefaults.PROFILE_LIMIT_POLICY,
    val memoryAllowFormula: String =
        "lifecycle_budget_reviews_memory_before_next_proot_start",
    val traceeAllowFormula: String = "single_proot_peak_multiplier_then_next_proot",
    val thermalUiAllowFormula: String = "not_part_of_p0_calibration_lifecycle_budget_can_block_expand",
    val effectiveParallelFormula: String =
        "single_proot_multiplier_limit_then_lifecycle_budget_then_android_capacity_executor",
    val actionSet: String = "KEEP_SINGLE_PROOT,REQUEST_NEXT_PROOT,HOLD_NEW_WORK",
    val reason: String = "safe_defaults_until_calibration_runs"
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation scope=$scope " +
            "canRun=$canRunNow blocker=$blocker overlay=$overlayStatus"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("proot_device_calibration_mode=${mode.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_enforcement_mode=${enforcementMode.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_enforcement_enabled=$enforcementEnabled")
            appendLine("proot_device_calibration_generated_at=$generatedAtMs")
            appendLine("proot_device_calibration_state=${state.name}")
            appendLine("proot_device_calibration_recommendation=${recommendation.name}")
            appendLine("proot_device_calibration_selected_goal=${selectedGoal.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_scope=${scope.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_android_execution_owner=$androidExecutionOwner")
            appendLine("proot_device_calibration_ubuntu_workload_generator_only=$ubuntuWorkloadGeneratorOnly")
            appendLine("proot_device_calibration_adb_required=$adbRequired")
            appendLine("proot_device_calibration_root_required=$rootRequired")
            appendLine("proot_device_calibration_product_signal_contract=${productSignalContract.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_lab_signal_contract=${labSignalContract.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_safe_bootstrap_profile=${safeBootstrapProfile.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_safe_bootstrap_single_proot_parallel=$safeBootstrapSingleProotParallel")
            appendLine("proot_device_calibration_safe_bootstrap_global_proot_max=$safeBootstrapGlobalProotMax")
            appendLine("proot_device_calibration_safe_bootstrap_low_priority_background_allowed=$safeBootstrapLowPriorityBackgroundAllowed")
            appendLine("proot_device_calibration_plan_protocol=${planProtocol.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_plan_log_path=${planLogPath.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_result_overlay_path=${resultOverlayPath.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_crash_recovery_key=${crashRecoveryKey.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_p0_test_count=$p0TestCount")
            appendLine("proot_device_calibration_p0_step_sequence=${p0StepSequence.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_baseline_test_enabled=$baselineTestEnabled")
            appendLine("proot_device_calibration_tracee_cap_test_enabled=$traceeCapTestEnabled")
            appendLine("proot_device_calibration_memory_cost_test_enabled=$memoryCostTestEnabled")
            appendLine("proot_device_calibration_thermal_ui_gate_enabled=$thermalUiGateEnabled")
            appendLine("proot_device_calibration_cpu_cost_deferred=$cpuCostDeferred")
            appendLine("proot_device_calibration_io_cost_deferred=$ioCostDeferred")
            appendLine("proot_device_calibration_mixed_workload_deferred=$mixedWorkloadDeferred")
            appendLine("proot_device_calibration_stop_condition=${stopCondition.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_current_lifecycle_state=${currentLifecycleState.name}")
            appendLine("proot_device_calibration_current_budget_state=${currentBudgetState.name}")
            appendLine("proot_device_calibration_current_telemetry_healthy=$currentTelemetryHealthy")
            appendLine("proot_device_calibration_current_live_tracees=$currentLiveTracees")
            appendLine("proot_device_calibration_current_fork_exec_window=$currentForkExecWindow")
            appendLine("proot_device_calibration_current_memory_signal=${currentMemorySignal.name}")
            appendLine("proot_device_calibration_current_risk_percent=$currentRiskPercent")
            appendLine("proot_device_calibration_current_bottleneck_axis=${currentBottleneckAxis.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_current_decision=${currentDecision.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_baseline_satisfied=$baselineSatisfied")
            appendLine("proot_device_calibration_can_run_now=$canRunNow")
            appendLine("proot_device_calibration_blocker=${blocker.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_conservative_tracee_soft_cap=$conservativeTraceeSoftCap")
            appendLine("proot_device_calibration_conservative_tracee_hard_cap=$conservativeTraceeHardCap")
            appendLine("proot_device_calibration_conservative_memory_worker_rss_kb=$conservativeMemoryWorkerRssKb")
            appendLine("proot_device_calibration_conservative_single_proot_balanced_parallel=$conservativeSingleProotBalancedParallel")
            appendLine("proot_device_calibration_overlay_schema=${overlaySchema.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_overlay_source=${overlaySource.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_overlay_status=${overlayStatus.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_overlay_valid=$overlayValid")
            appendLine("proot_device_calibration_overlay_load_error=${overlayLoadError.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_overlay_method=${overlayMethod.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_overlay_upper_bound_measured=$overlayUpperBoundMeasured")
            appendLine("proot_device_calibration_overlay_healthy_stable_tracee_cap=$overlayHealthyStableTraceeCap")
            appendLine("proot_device_calibration_overlay_budget_knee_tracees=$overlayBudgetKneeTracees")
            appendLine("proot_device_calibration_overlay_budget_knee_used_for_capacity=$overlayBudgetKneeUsedForCapacity")
            appendLine("proot_device_calibration_overlay_budget_knee_policy=${overlayBudgetKneePolicy.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_overlay_model_guard_knee_tracees=$overlayModelGuardKneeTracees")
            appendLine("proot_device_calibration_overlay_safe_tested_max_tracees=$overlaySafeTestedMaxTracees")
            appendLine("proot_device_calibration_overlay_measured_max_tracees=$overlayMeasuredMaxTracees")
            appendLine("proot_device_calibration_overlay_default_start_cap=$overlayDefaultStartCap")
            appendLine("proot_device_calibration_overlay_tracee_max_cap=$overlayTraceeMaxCap")
            appendLine("proot_device_calibration_single_proot_peak_tracees=$singleProotPeakTracees")
            appendLine("proot_device_calibration_single_proot_limit_tracees=$singleProotQueueUntilTracees")
            appendLine("proot_device_calibration_single_proot_queue_until_tracees=$singleProotQueueUntilTracees")
            appendLine("proot_device_calibration_second_proot_trigger_tracees=$secondProotTriggerTracees")
            appendLine("proot_device_calibration_next_proot_trigger_tracees=$secondProotTriggerTracees")
            appendLine("proot_device_calibration_overflow_headroom_tracees=$overflowHeadroomTracees")
            appendLine("proot_device_calibration_single_proot_overflow_percent=$singleProotOverflowPercent")
            appendLine("proot_device_calibration_queue_headroom_percent=$queueHeadroomPercent")
            appendLine("proot_device_calibration_second_proot_trigger_headroom_percent=$secondProotTriggerHeadroomPercent")
            appendLine("proot_device_calibration_next_proot_trigger_headroom_percent=$secondProotTriggerHeadroomPercent")
            appendLine("proot_device_calibration_overflow_percent_base=${overflowPercentBase.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_queue_strategy_percent_base=${queueStrategyPercentBase.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_queue_strategy_policy=${queueStrategyPolicy.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_low_power_profile_limit=$lowPowerProfileLimit")
            appendLine("proot_device_calibration_balanced_profile_limit=$balancedProfileLimit")
            appendLine("proot_device_calibration_high_performance_profile_limit=$highPerformanceProfileLimit")
            appendLine("proot_device_calibration_profile_limit_policy=${profileLimitPolicy.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_memory_allow_formula=${memoryAllowFormula.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_tracee_allow_formula=${traceeAllowFormula.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_thermal_ui_allow_formula=${thermalUiAllowFormula.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_effective_parallel_formula=${effectiveParallelFormula.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_action_set=${actionSet.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_reason=${reason.toCalibrationEnvValue()}")
            appendLine("proot_device_calibration_boundary=observe_only_no_probe_start_no_pool_resize_no_queue_no_enforcement")
        }
    }
}

object RuntimeProotDeviceCalibrationDryRun {
    fun evaluate(
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot,
        pressureConsumer: RuntimePressureConsumerSnapshot,
        backgroundDecay: RuntimeBackgroundDecayDryRunSnapshot,
        budgetPressure: RuntimeBudgetPressureDryRunSnapshot,
        prootPoolPlan: RuntimeProotPoolPlanDryRunSnapshot,
        overlay: RuntimeProotDeviceCalibrationOverlay = RuntimeProotDeviceCalibrationOverlay(),
        now: Long = System.currentTimeMillis()
    ): RuntimeProotDeviceCalibrationDryRunSnapshot {
        val telemetryHealthy = prootTelemetryHealth.canaryHealthy &&
            prootTelemetryHealth.blocker == "none"
        val lowBudgetRisk = budgetPressure.overallState.severity() <=
            RuntimeBudgetState.NEAR_BUDGET.severity()
        val lowProotRisk = pressureConsumer.prootPressureScore <= 20 &&
            pressureConsumer.forkExecEventsInWindow == 0
        val baselineSatisfied = telemetryHealthy &&
            lowBudgetRisk &&
            lowProotRisk &&
            pressureConsumer.liveTraceeCount <= 1
        val blocker = resolveBlocker(
            telemetryHealthy = telemetryHealthy,
            lowBudgetRisk = lowBudgetRisk,
            lowProotRisk = lowProotRisk,
            liveTracees = pressureConsumer.liveTraceeCount,
            prootTelemetryHealth = prootTelemetryHealth
        )
        val canRunNow = baselineSatisfied
        val state = when {
            overlay.valid -> RuntimeProotDeviceCalibrationState.RESULT_OVERLAY_READY
            !telemetryHealthy || !lowBudgetRisk -> RuntimeProotDeviceCalibrationState.BLOCKED
            canRunNow -> RuntimeProotDeviceCalibrationState.PLAN_READY
            else -> RuntimeProotDeviceCalibrationState.SAFE_BOOTSTRAP
        }
        val recommendation = when (state) {
            RuntimeProotDeviceCalibrationState.BLOCKED ->
                RuntimeProotDeviceCalibrationRecommendation.WAIT_FOR_SAFE_BASELINE
            RuntimeProotDeviceCalibrationState.SAFE_BOOTSTRAP ->
                RuntimeProotDeviceCalibrationRecommendation.USE_SAFE_BOOTSTRAP
            RuntimeProotDeviceCalibrationState.PLAN_READY ->
                RuntimeProotDeviceCalibrationRecommendation.RUN_P0_CALIBRATION
            RuntimeProotDeviceCalibrationState.RESULT_OVERLAY_READY ->
                RuntimeProotDeviceCalibrationRecommendation.APPLY_LOCAL_OVERLAY
        }
        return RuntimeProotDeviceCalibrationDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendation,
            currentLifecycleState = backgroundDecay.lifecycleState,
            currentBudgetState = budgetPressure.overallState,
            currentTelemetryHealthy = telemetryHealthy,
            currentLiveTracees = pressureConsumer.liveTraceeCount,
            currentForkExecWindow = pressureConsumer.forkExecEventsInWindow,
            currentMemorySignal = pressureConsumer.rssPressureLevel,
            currentRiskPercent = prootPoolPlan.resourceEquationRiskPercent,
            currentBottleneckAxis = prootPoolPlan.resourceEquationBottleneckAxis,
            currentDecision = prootPoolPlan.resourceEquationDecision,
            baselineSatisfied = baselineSatisfied,
            canRunNow = canRunNow,
            blocker = blocker,
            conservativeTraceeSoftCap = if (overlay.valid) {
                overlay.traceeSoftCap
            } else {
                minOf(prootPoolPlan.adaptiveBackgroundLiveTraceeSoftCap.coerceAtLeast(1), 8)
            },
            conservativeTraceeHardCap = if (overlay.valid) {
                overlay.traceeHardCap
            } else {
                prootPoolPlan.adaptiveHardStopLiveTracees.coerceAtLeast(16)
            },
            conservativeMemoryWorkerRssKb = overlay.memoryWorkerRssKb,
            overlaySource = overlay.source,
            overlayStatus = overlay.status,
            overlayValid = overlay.valid,
            overlayLoadError = overlay.loadError,
            overlayMethod = overlay.calibrationMethod,
            overlayUpperBoundMeasured = overlay.upperBoundMeasured,
            overlayHealthyStableTraceeCap = overlay.healthyStableTraceeCap,
            overlayBudgetKneeTracees = overlay.budgetKneeTracees,
            overlayBudgetKneeUsedForCapacity = overlay.budgetKneeUsedForCapacity,
            overlayBudgetKneePolicy = overlay.budgetKneePolicy,
            overlayModelGuardKneeTracees = overlay.modelGuardKneeTracees,
            overlaySafeTestedMaxTracees = overlay.safeTestedMaxTracees,
            overlayMeasuredMaxTracees = overlay.measuredMaxTracees,
            overlayDefaultStartCap = overlay.defaultStartCap,
            overlayTraceeMaxCap = overlay.traceeMaxCap,
            singleProotPeakTracees = overlay.singleProotPeakTracees,
            singleProotQueueUntilTracees = overlay.singleProotQueueUntilTracees,
            secondProotTriggerTracees = overlay.secondProotTriggerTracees,
            overflowHeadroomTracees = overlay.overflowHeadroomTracees,
            singleProotOverflowPercent = overlay.singleProotOverflowPercent,
            queueHeadroomPercent = overlay.queueHeadroomPercent,
            secondProotTriggerHeadroomPercent = overlay.secondProotTriggerHeadroomPercent,
            overflowPercentBase = overlay.overflowPercentBase,
            queueStrategyPercentBase = overlay.queueStrategyPercentBase,
            queueStrategyPolicy = overlay.queueStrategyPolicy,
            lowPowerProfileLimit = overlay.lowPowerProfileLimit,
            balancedProfileLimit = overlay.balancedProfileLimit,
            highPerformanceProfileLimit = overlay.highPerformanceProfileLimit,
            profileLimitPolicy = overlay.profileLimitPolicy,
            reason = "telemetry=$telemetryHealthy,budget=${budgetPressure.overallState.name}," +
                "score=${pressureConsumer.prootPressureScore},live=${pressureConsumer.liveTraceeCount}," +
                "risk=${prootPoolPlan.resourceEquationRiskPercent},blocker=$blocker," +
                "overlay=${overlay.status}/${overlay.valid}"
        )
    }

    private fun resolveBlocker(
        telemetryHealthy: Boolean,
        lowBudgetRisk: Boolean,
        lowProotRisk: Boolean,
        liveTracees: Int,
        prootTelemetryHealth: ProotTelemetryHealthDryRunSnapshot
    ): String {
        return when {
            !telemetryHealthy -> prootTelemetryHealth.blocker.takeIf { it != "none" }
                ?: "telemetry_not_healthy"
            !lowBudgetRisk -> "budget_not_ready_for_calibration"
            !lowProotRisk -> "proot_pressure_not_quiet"
            liveTracees > 1 -> "live_tracees_not_clean_baseline"
            else -> "none"
        }
    }

    private fun RuntimeBudgetState.severity(): Int {
        return when (this) {
            RuntimeBudgetState.HEALTHY -> 0
            RuntimeBudgetState.NEAR_BUDGET -> 1
            RuntimeBudgetState.SOFT_PRESSURE -> 2
            RuntimeBudgetState.HARD_PRESSURE -> 3
            RuntimeBudgetState.THREATENING_KF -> 4
            RuntimeBudgetState.REPEAT_OFFENDER -> 5
            RuntimeBudgetState.QUARANTINED -> 6
        }
    }
}

private fun String?.toCalibrationEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
