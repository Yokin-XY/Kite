package com.kftest.app.foundation.runtime

enum class RuntimeProcessObservationValidationOverallStatus {
    PASS,
    PASS_WITH_LIMITATIONS,
    BLOCKED_BY_OBSERVATION_GAP,
    NEEDS_MANUAL_RETEST
}

enum class RuntimeProcessObservationValidationCheckStatus {
    PASS,
    PASS_WITH_LIMITATIONS,
    BLOCKING,
    NEEDS_MANUAL_RETEST,
    NOT_RECORDED
}

data class RuntimeProcessObservationValidationEvidence(
    val psFormatStatus: RuntimeProcessObservationValidationCheckStatus? = null,
    val pgidSidStatus: RuntimeProcessObservationValidationCheckStatus? = null,
    val pidFileWorkspaceStatus: RuntimeProcessObservationValidationCheckStatus? = null,
    val pidFileRunTmpStatus: RuntimeProcessObservationValidationCheckStatus? = null,
    val hostContainerPidCorrelationStatus: RuntimeProcessObservationValidationCheckStatus? = null,
    val memoryRssVmSizeStatus: RuntimeProcessObservationValidationCheckStatus? = null,
    val unmanagedKillObservationStatus: RuntimeProcessObservationValidationCheckStatus? = null,
    val manifestMatchingStatus: RuntimeProcessObservationValidationCheckStatus? = null,
    val psPidPpidUnavailable: Boolean = false,
    val unmanagedKillAutoRecovered: Boolean = false,
    val weakManifestMatchTriggeredRealExecution: Boolean = false,
    val coreOrProotCoreDowngradedByManifest: Boolean = false,
    val stalePidFileTriggeredExecution: Boolean = false,
    val memoryMissingCrashed: Boolean = false,
    val hostProcessTerminatorCalledByProbe: Boolean = false,
    val prootExecutorCalledByProbe: Boolean = false
)

data class RuntimeProcessObservationValidationReport(
    val mode: String = "runtime_process_observation_validation_report_v0",
    val validationTimestamp: Long = 0L,
    val deviceModel: String = "unknown",
    val androidVersion: String = "unknown",
    val containerRootfs: String = "unknown",
    val psFormatStatus: RuntimeProcessObservationValidationCheckStatus =
        RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
    val pgidSidStatus: RuntimeProcessObservationValidationCheckStatus =
        RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
    val pidFileWorkspaceStatus: RuntimeProcessObservationValidationCheckStatus =
        RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
    val pidFileRunTmpStatus: RuntimeProcessObservationValidationCheckStatus =
        RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
    val hostContainerPidCorrelationStatus: RuntimeProcessObservationValidationCheckStatus =
        RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
    val memoryRssVmSizeStatus: RuntimeProcessObservationValidationCheckStatus =
        RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
    val unmanagedKillObservationStatus: RuntimeProcessObservationValidationCheckStatus =
        RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
    val manifestMatchingStatus: RuntimeProcessObservationValidationCheckStatus =
        RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
    val overallStatus: RuntimeProcessObservationValidationOverallStatus =
        RuntimeProcessObservationValidationOverallStatus.NEEDS_MANUAL_RETEST,
    val blockingIssues: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val nextRecommendedStep: String = "run_real_device_observation_validation_checklist",
    val hostProcessTerminatorCalledByProbe: Boolean = false,
    val prootExecutorCalledByProbe: Boolean = false,
    val invariantChecks: List<String> = DEFAULT_INVARIANT_CHECKS,
    val boundary: String = "manual_validation_report_only_no_runtime_execution"
) {
    fun toEnvText(): String {
        return buildString {
            appendLine("runtime_process_observation_validation_mode=${mode.toObservationValidationEnvValue()}")
            appendLine("runtime_process_observation_validation_timestamp=$validationTimestamp")
            appendLine("runtime_process_observation_validation_device_model=${deviceModel.toObservationValidationEnvValue()}")
            appendLine("runtime_process_observation_validation_android_version=${androidVersion.toObservationValidationEnvValue()}")
            appendLine("runtime_process_observation_validation_container_rootfs=${containerRootfs.toObservationValidationEnvValue()}")
            appendLine("runtime_process_observation_validation_ps_format_status=${psFormatStatus.name}")
            appendLine("runtime_process_observation_validation_pgid_sid_status=${pgidSidStatus.name}")
            appendLine("runtime_process_observation_validation_pid_file_workspace_status=${pidFileWorkspaceStatus.name}")
            appendLine("runtime_process_observation_validation_pid_file_run_tmp_status=${pidFileRunTmpStatus.name}")
            appendLine("runtime_process_observation_validation_host_container_pid_correlation_status=${hostContainerPidCorrelationStatus.name}")
            appendLine("runtime_process_observation_validation_memory_rss_vmsize_status=${memoryRssVmSizeStatus.name}")
            appendLine("runtime_process_observation_validation_unmanaged_kill_observation_status=${unmanagedKillObservationStatus.name}")
            appendLine("runtime_process_observation_validation_manifest_matching_status=${manifestMatchingStatus.name}")
            appendLine("runtime_process_observation_validation_overall_status=${overallStatus.name}")
            appendLine("runtime_process_observation_validation_blocking_issue_count=${blockingIssues.size}")
            appendLine("runtime_process_observation_validation_blocking_issues=${blockingIssues.joinToString(";").toObservationValidationEnvValue()}")
            appendLine("runtime_process_observation_validation_warning_count=${warnings.size}")
            appendLine("runtime_process_observation_validation_warnings=${warnings.joinToString(";").toObservationValidationEnvValue()}")
            appendLine("runtime_process_observation_validation_next_recommended_step=${nextRecommendedStep.toObservationValidationEnvValue()}")
            appendLine("runtime_process_observation_validation_host_process_terminator_called_by_probe=$hostProcessTerminatorCalledByProbe")
            appendLine("runtime_process_observation_validation_proot_executor_called_by_probe=$prootExecutorCalledByProbe")
            appendLine("runtime_process_observation_validation_invariant_checks=${invariantChecks.joinToString(";").toObservationValidationEnvValue()}")
            appendLine("runtime_process_observation_validation_boundary=${boundary.toObservationValidationEnvValue()}")
        }
    }

    companion object {
        val DEFAULT_INVARIANT_CHECKS = listOf(
            "host_process_terminator_not_called_by_probe_or_report",
            "proot_capacity_executor_not_called_by_probe_or_report",
            "validation_report_is_observe_only_not_executor"
        )

        fun fromProbe(
            probe: RuntimeProcessObservationProbeSnapshot,
            validationTimestamp: Long =
                probe.probeTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
            deviceModel: String = "unknown",
            androidVersion: String = "unknown",
            containerRootfs: String = "unknown",
            evidence: RuntimeProcessObservationValidationEvidence =
                RuntimeProcessObservationValidationEvidence(),
            extraWarnings: List<String> = emptyList(),
            extraBlockingIssues: List<String> = emptyList()
        ): RuntimeProcessObservationValidationReport {
            val blockingIssues = blockingIssuesFor(evidence) + extraBlockingIssues
            val warnings = (probe.warnings + warningsForProbeLimitations(probe) + extraWarnings)
                .distinct()
            return resolved(
                validationTimestamp = validationTimestamp,
                deviceModel = deviceModel,
                androidVersion = androidVersion,
                containerRootfs = containerRootfs,
                psFormatStatus = evidence.psFormatStatus ?: psFormatStatusFrom(probe),
                pgidSidStatus = evidence.pgidSidStatus ?: pgidSidStatusFrom(probe),
                pidFileWorkspaceStatus = evidence.pidFileWorkspaceStatus
                    ?: pidFileWorkspaceStatusFrom(probe),
                pidFileRunTmpStatus = evidence.pidFileRunTmpStatus ?: pidFileRunTmpStatusFrom(probe),
                hostContainerPidCorrelationStatus = evidence.hostContainerPidCorrelationStatus
                    ?: hostContainerPidCorrelationStatusFrom(probe),
                memoryRssVmSizeStatus = evidence.memoryRssVmSizeStatus ?: memoryStatusFrom(probe),
                unmanagedKillObservationStatus = evidence.unmanagedKillObservationStatus
                    ?: unmanagedKillStatusFrom(probe),
                manifestMatchingStatus = evidence.manifestMatchingStatus
                    ?: RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST,
                blockingIssues = blockingIssues,
                warnings = warnings,
                nextRecommendedStep = if (probe.needsRealDeviceValidation) {
                    "run_real_device_observation_validation_checklist"
                } else {
                    "record_validation_report_and_keep_observe_only"
                },
                hostProcessTerminatorCalledByProbe = evidence.hostProcessTerminatorCalledByProbe,
                prootExecutorCalledByProbe = evidence.prootExecutorCalledByProbe
            )
        }

        fun resolved(
            validationTimestamp: Long = 0L,
            deviceModel: String = "unknown",
            androidVersion: String = "unknown",
            containerRootfs: String = "unknown",
            psFormatStatus: RuntimeProcessObservationValidationCheckStatus =
                RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
            pgidSidStatus: RuntimeProcessObservationValidationCheckStatus =
                RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
            pidFileWorkspaceStatus: RuntimeProcessObservationValidationCheckStatus =
                RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
            pidFileRunTmpStatus: RuntimeProcessObservationValidationCheckStatus =
                RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
            hostContainerPidCorrelationStatus: RuntimeProcessObservationValidationCheckStatus =
                RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
            memoryRssVmSizeStatus: RuntimeProcessObservationValidationCheckStatus =
                RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
            unmanagedKillObservationStatus: RuntimeProcessObservationValidationCheckStatus =
                RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
            manifestMatchingStatus: RuntimeProcessObservationValidationCheckStatus =
                RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED,
            blockingIssues: List<String> = emptyList(),
            warnings: List<String> = emptyList(),
            nextRecommendedStep: String = "run_real_device_observation_validation_checklist",
            hostProcessTerminatorCalledByProbe: Boolean = false,
            prootExecutorCalledByProbe: Boolean = false
        ): RuntimeProcessObservationValidationReport {
            val statuses = listOf(
                psFormatStatus,
                pgidSidStatus,
                pidFileWorkspaceStatus,
                pidFileRunTmpStatus,
                hostContainerPidCorrelationStatus,
                memoryRssVmSizeStatus,
                unmanagedKillObservationStatus,
                manifestMatchingStatus
            )
            val effectiveBlockingIssues = blockingIssues.distinct()
            val effectiveWarnings = warnings.distinct()
            return RuntimeProcessObservationValidationReport(
                validationTimestamp = validationTimestamp,
                deviceModel = deviceModel,
                androidVersion = androidVersion,
                containerRootfs = containerRootfs,
                psFormatStatus = psFormatStatus,
                pgidSidStatus = pgidSidStatus,
                pidFileWorkspaceStatus = pidFileWorkspaceStatus,
                pidFileRunTmpStatus = pidFileRunTmpStatus,
                hostContainerPidCorrelationStatus = hostContainerPidCorrelationStatus,
                memoryRssVmSizeStatus = memoryRssVmSizeStatus,
                unmanagedKillObservationStatus = unmanagedKillObservationStatus,
                manifestMatchingStatus = manifestMatchingStatus,
                overallStatus = overallStatusFor(
                    statuses = statuses,
                    blockingIssues = effectiveBlockingIssues,
                    warnings = effectiveWarnings
                ),
                blockingIssues = effectiveBlockingIssues,
                warnings = effectiveWarnings,
                nextRecommendedStep = nextRecommendedStep,
                hostProcessTerminatorCalledByProbe = hostProcessTerminatorCalledByProbe,
                prootExecutorCalledByProbe = prootExecutorCalledByProbe
            )
        }

        private fun overallStatusFor(
            statuses: List<RuntimeProcessObservationValidationCheckStatus>,
            blockingIssues: List<String>,
            warnings: List<String>
        ): RuntimeProcessObservationValidationOverallStatus {
            return when {
                blockingIssues.isNotEmpty() ||
                    statuses.any { it == RuntimeProcessObservationValidationCheckStatus.BLOCKING } ->
                    RuntimeProcessObservationValidationOverallStatus.BLOCKED_BY_OBSERVATION_GAP
                statuses.any {
                    it == RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST ||
                        it == RuntimeProcessObservationValidationCheckStatus.NOT_RECORDED
                } -> RuntimeProcessObservationValidationOverallStatus.NEEDS_MANUAL_RETEST
                warnings.isNotEmpty() ||
                    statuses.any {
                        it == RuntimeProcessObservationValidationCheckStatus.PASS_WITH_LIMITATIONS
                    } -> RuntimeProcessObservationValidationOverallStatus.PASS_WITH_LIMITATIONS
                else -> RuntimeProcessObservationValidationOverallStatus.PASS
            }
        }

        private fun blockingIssuesFor(
            evidence: RuntimeProcessObservationValidationEvidence
        ): List<String> {
            val issues = mutableListOf<String>()
            if (evidence.psPidPpidUnavailable) issues += "ps_pid_ppid_unavailable"
            if (evidence.unmanagedKillAutoRecovered) {
                issues += "unmanaged_kill_incorrectly_auto_recovered"
            }
            if (evidence.weakManifestMatchTriggeredRealExecution) {
                issues += "weak_manifest_match_triggered_real_execution"
            }
            if (evidence.coreOrProotCoreDowngradedByManifest) {
                issues += "core_or_proot_core_downgraded_by_manifest"
            }
            if (evidence.stalePidFileTriggeredExecution) {
                issues += "stale_pid_file_misclassified_and_triggered_execution"
            }
            if (evidence.memoryMissingCrashed) issues += "memory_missing_caused_crash"
            if (evidence.hostProcessTerminatorCalledByProbe) {
                issues += "host_process_terminator_called_by_probe_or_report"
            }
            if (evidence.prootExecutorCalledByProbe) {
                issues += "proot_capacity_executor_called_by_probe_or_report"
            }
            return issues
        }

        private fun psFormatStatusFrom(
            probe: RuntimeProcessObservationProbeSnapshot
        ): RuntimeProcessObservationValidationCheckStatus {
            return when (probe.containerPsFormat) {
                RuntimeProcessObservationProbe.NEW_PS_FORMAT ->
                    RuntimeProcessObservationValidationCheckStatus.PASS
                RuntimeProcessObservationProbe.FALLBACK_PS_FORMAT ->
                    RuntimeProcessObservationValidationCheckStatus.PASS_WITH_LIMITATIONS
                "container_ps_unavailable" ->
                    RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
                else -> RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
            }
        }

        private fun pgidSidStatusFrom(
            probe: RuntimeProcessObservationProbeSnapshot
        ): RuntimeProcessObservationValidationCheckStatus {
            return when {
                probe.pgidSupported && probe.sidSupported ->
                    RuntimeProcessObservationValidationCheckStatus.PASS
                probe.pgidSupported || probe.sidSupported ->
                    RuntimeProcessObservationValidationCheckStatus.PASS_WITH_LIMITATIONS
                else -> RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
            }
        }

        private fun pidFileWorkspaceStatusFrom(
            probe: RuntimeProcessObservationProbeSnapshot
        ): RuntimeProcessObservationValidationCheckStatus {
            return when (probe.workspacePidFileMappingStatus) {
                "workspace_path_declared_safe_mapping_needs_real_device_validation" ->
                    RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
                "workspace_path_declared_but_rejected" ->
                    RuntimeProcessObservationValidationCheckStatus.PASS_WITH_LIMITATIONS
                else -> RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
            }
        }

        private fun pidFileRunTmpStatusFrom(
            probe: RuntimeProcessObservationProbeSnapshot
        ): RuntimeProcessObservationValidationCheckStatus {
            return when (probe.runTmpPidFileMappingStatus) {
                "run_and_tmp_declared_safe_rootfs_mapping_needs_real_device_validation",
                "run_or_tmp_declared_safe_rootfs_mapping_needs_real_device_validation" ->
                    RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
                "run_tmp_declared_but_rejected" ->
                    RuntimeProcessObservationValidationCheckStatus.PASS_WITH_LIMITATIONS
                else -> RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
            }
        }

        private fun hostContainerPidCorrelationStatusFrom(
            probe: RuntimeProcessObservationProbeSnapshot
        ): RuntimeProcessObservationValidationCheckStatus {
            return when {
                probe.hostPidContainerPidCorrelation == "observed_pid_matches_registered_pid" ||
                    probe.hostPidContainerPidCorrelation == "host_proc_direct_pid_observation" ->
                    RuntimeProcessObservationValidationCheckStatus.PASS
                probe.hostPidContainerPidCorrelation.contains("differs") ->
                    RuntimeProcessObservationValidationCheckStatus.PASS_WITH_LIMITATIONS
                else -> RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
            }
        }

        private fun memoryStatusFrom(
            probe: RuntimeProcessObservationProbeSnapshot
        ): RuntimeProcessObservationValidationCheckStatus {
            return when (probe.memoryObservationStatus) {
                "rss_and_vmsize_observed" -> RuntimeProcessObservationValidationCheckStatus.PASS
                "rss_observed_vmsize_missing",
                "vmsize_observed_rss_missing",
                "memory_counters_missing_or_unknown" ->
                    RuntimeProcessObservationValidationCheckStatus.PASS_WITH_LIMITATIONS
                else -> RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
            }
        }

        private fun unmanagedKillStatusFrom(
            probe: RuntimeProcessObservationProbeSnapshot
        ): RuntimeProcessObservationValidationCheckStatus {
            return when (probe.unmanagedProcessObservationStatus) {
                "disappeared_snapshot_only_no_auto_registration",
                "unmanaged_observed_only_no_auto_registration" ->
                    RuntimeProcessObservationValidationCheckStatus.PASS
                else -> RuntimeProcessObservationValidationCheckStatus.NEEDS_MANUAL_RETEST
            }
        }

        private fun warningsForProbeLimitations(
            probe: RuntimeProcessObservationProbeSnapshot
        ): List<String> {
            val warnings = mutableListOf<String>()
            if (probe.containerPsFormat == RuntimeProcessObservationProbe.FALLBACK_PS_FORMAT) {
                warnings += "ps_fallback_without_pgid_sid"
            }
            if (probe.memoryObservationStatus == "memory_counters_missing_or_unknown") {
                warnings += "memory_missing_is_limitation_not_crash"
            }
            if (probe.hostPidContainerPidCorrelation.contains("unknown")) {
                warnings += "host_container_pid_correlation_needs_manual_observation"
            }
            return warnings
        }
    }
}

private fun String?.toObservationValidationEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(320)
}
