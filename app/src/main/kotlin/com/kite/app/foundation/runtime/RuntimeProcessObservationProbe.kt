package com.kite.app.foundation.runtime

data class RuntimeProcessObservationProbeSnapshot(
    val mode: String = "runtime_process_observation_probe_v0",
    val probeEnabled: Boolean = true,
    val enforcementEnabled: Boolean = false,
    val probeTimestamp: Long = 0L,
    val containerPsAvailable: Boolean = false,
    val containerPsFormat: String = "unknown",
    val pgidSupported: Boolean = false,
    val sidSupported: Boolean = false,
    val rssSupported: Boolean = false,
    val vmSizeSupported: Boolean = false,
    val pidFileReadSupported: Boolean = false,
    val workspacePidFileMappingStatus: String = "not_declared",
    val runTmpPidFileMappingStatus: String = "not_declared",
    val pidFileProbeStatus: String = "not_declared_manual_probe_required",
    val hostProcFallbackAvailable: Boolean = false,
    val hostPidContainerPidCorrelation: String = "unknown_needs_real_device_validation",
    val processGroupObservationStatus: String = "pgid_sid_missing_fallback",
    val manualKillObservationStatus: String = "manual_kill_requires_real_device_checklist",
    val unmanagedProcessObservationStatus: String = "not_observed",
    val memoryObservationStatus: String = "memory_counters_missing_or_unknown",
    val needsRealDeviceValidation: Boolean = true,
    val needsRealDeviceValidationCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val realDeviceValidationMarkers: List<String> =
        RuntimeProcessObservationProbe.DEFAULT_REAL_DEVICE_MARKERS,
    val manualValidationChecklist: List<String> =
        RuntimeProcessObservationProbe.DEFAULT_MANUAL_VALIDATION_CHECKLIST,
    val boundary: String =
        "observation_probe_only_no_kill_restart_reclaim_quarantine_or_proot_capacity_execution"
) {
    fun toEnvText(): String {
        return buildString {
            appendLine("runtime_process_observation_probe_mode=${mode.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_enabled=$probeEnabled")
            appendLine("runtime_process_observation_probe_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_process_observation_probe_timestamp=$probeTimestamp")
            appendLine("runtime_process_observation_probe_container_ps_available=$containerPsAvailable")
            appendLine("runtime_process_observation_probe_container_ps_format=${containerPsFormat.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_pgid_supported=$pgidSupported")
            appendLine("runtime_process_observation_probe_sid_supported=$sidSupported")
            appendLine("runtime_process_observation_probe_rss_supported=$rssSupported")
            appendLine("runtime_process_observation_probe_vmsize_supported=$vmSizeSupported")
            appendLine("runtime_process_observation_probe_pid_file_read_supported=$pidFileReadSupported")
            appendLine("runtime_process_observation_probe_workspace_pid_file_mapping_status=${workspacePidFileMappingStatus.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_run_tmp_pid_file_mapping_status=${runTmpPidFileMappingStatus.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_pid_file_probe_status=${pidFileProbeStatus.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_host_proc_fallback_available=$hostProcFallbackAvailable")
            appendLine("runtime_process_observation_probe_host_pid_correlation_status=${hostPidContainerPidCorrelation.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_process_group_observation_status=${processGroupObservationStatus.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_manual_kill_observation_status=${manualKillObservationStatus.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_unmanaged_kill_observation_status=${unmanagedProcessObservationStatus.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_memory_observation_status=${memoryObservationStatus.toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_needs_real_device_validation=$needsRealDeviceValidation")
            appendLine("runtime_process_observation_probe_needs_real_device_validation_count=$needsRealDeviceValidationCount")
            appendLine("runtime_process_observation_probe_warnings=${warnings.joinToString(";").toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_real_device_validation_markers=${realDeviceValidationMarkers.joinToString(";").toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_manual_validation_checklist=${manualValidationChecklist.joinToString(";").toObservationProbeEnvValue()}")
            appendLine("runtime_process_observation_probe_boundary=${boundary.toObservationProbeEnvValue()}")
        }
    }
}

object RuntimeProcessObservationProbe {
    const val NEW_PS_FORMAT = "ps_eo_pid_ppid_pgid_sid_stat_comm_args"
    const val FALLBACK_PS_FORMAT = "ps_eo_pid_ppid_stat_comm_args"

    val DEFAULT_REAL_DEVICE_MARKERS = listOf(
        "pgid_sid_stability_under_android_proot",
        "pid_file_mapped_path_against_real_rootfs",
        "ps_output_format_compatibility",
        "host_pid_container_pid_correlation",
        "memory_rss_vmsize_accuracy_under_android_proot",
        "future_process_group_kill_behavior_reliability"
    )

    val DEFAULT_MANUAL_VALIDATION_CHECKLIST = listOf(
        "ps -eo pid,ppid,pgid,sid,stat,comm,args",
        "pgrep -a sleep",
        "sleep 300 &",
        "echo $! > /workspace/.kf/test-sleep.pid",
        "cat /workspace/.kf/test-sleep.pid",
        "kill -0 <pid>",
        "kill <pid>",
        "pgrep -a sleep"
    )

    fun evaluate(
        processSnapshot: ContainerProcessSnapshot = ContainerProcessSnapshot(),
        roots: List<RuntimeRootSnapshot> = emptyList(),
        manifest: RuntimeProcessUnitManifest = RuntimeProcessUnitManifest.default(),
        now: Long = System.currentTimeMillis()
    ): RuntimeProcessObservationProbeSnapshot {
        val source = processSnapshot.collectionSource
        val processRecords = processSnapshot.processes
        val containerPsAvailable = source.contains("container_ps", ignoreCase = true)
        val pgidSupported = processRecords.any { it.processGroupId != null } ||
            roots.any { it.rootProcessGroupId != null }
        val sidSupported = processRecords.any { it.sessionId != null } ||
            roots.any { it.rootSessionId != null }
        val rssSupported = processSnapshot.resourceSnapshot.rssKb > 0L ||
            processRecords.any { (it.rssKb ?: 0L) > 0L } ||
            roots.any { it.rssKb > 0L }
        val vmSizeSupported = processSnapshot.resourceSnapshot.vmSizeKb > 0L ||
            processRecords.any { (it.vmSizeKb ?: 0L) > 0L } ||
            roots.any { it.vmSizeKb > 0L }
        val pidFiles = manifest.units.mapNotNull { it.match.pidFile?.takeIf { path -> path.isNotBlank() } }
        val unsafePidFileCount = pidFiles.count { !RuntimeProcessUnitPidFilePathPolicy.isAllowed(it) }
        val pidFileReadSupported = pidFiles.any(RuntimeProcessUnitPidFilePathPolicy::isAllowed)
        val warnings = warningsFor(
            containerPsAvailable = containerPsAvailable,
            pgidSupported = pgidSupported,
            sidSupported = sidSupported,
            rssSupported = rssSupported,
            vmSizeSupported = vmSizeSupported,
            pidFiles = pidFiles,
            unsafePidFileCount = unsafePidFileCount,
            roots = roots,
            source = source
        )
        val markers = markersFor(
            pgidSupported = pgidSupported,
            sidSupported = sidSupported,
            pidFiles = pidFiles,
            rssSupported = rssSupported,
            vmSizeSupported = vmSizeSupported,
            source = source,
            roots = roots
        )
        return RuntimeProcessObservationProbeSnapshot(
            probeTimestamp = now,
            containerPsAvailable = containerPsAvailable,
            containerPsFormat = containerPsFormat(
                containerPsAvailable = containerPsAvailable,
                processRecords = processRecords,
                pgidSupported = pgidSupported,
                sidSupported = sidSupported
            ),
            pgidSupported = pgidSupported,
            sidSupported = sidSupported,
            rssSupported = rssSupported,
            vmSizeSupported = vmSizeSupported,
            pidFileReadSupported = pidFileReadSupported,
            workspacePidFileMappingStatus = workspacePidFileMappingStatus(pidFiles),
            runTmpPidFileMappingStatus = runTmpPidFileMappingStatus(pidFiles),
            pidFileProbeStatus = pidFileProbeStatus(pidFiles, unsafePidFileCount),
            hostProcFallbackAvailable = source.contains("host_proc", ignoreCase = true) ||
                source.contains("host_fallback", ignoreCase = true) ||
                processSnapshot.resourceSnapshot.source.contains("host", ignoreCase = true),
            hostPidContainerPidCorrelation = hostPidContainerPidCorrelation(roots, source),
            processGroupObservationStatus = processGroupObservationStatus(pgidSupported, sidSupported),
            manualKillObservationStatus = manualKillObservationStatus(roots),
            unmanagedProcessObservationStatus = unmanagedProcessObservationStatus(roots),
            memoryObservationStatus = memoryObservationStatus(rssSupported, vmSizeSupported),
            needsRealDeviceValidation = markers.isNotEmpty(),
            needsRealDeviceValidationCount = markers.size,
            warnings = warnings,
            realDeviceValidationMarkers = markers
        )
    }

    fun parsePsLineForProbe(line: String): ContainerProcessRecord? {
        return ContainerProcessStore.parseContainerPsLineForTesting(line)
    }

    fun pidFilePathAllowed(path: String): Boolean {
        return RuntimeProcessUnitPidFilePathPolicy.isAllowed(path)
    }

    fun pidFileReadStatusFor(
        read: RuntimeProcessUnitPidFileRead,
        observedPids: Set<Int>,
        commandMatches: Boolean = true
    ): String {
        if (read.state != RuntimeProcessUnitPidFileReadState.RESOLVED || read.pid == null) {
            return "pid_file_${read.state.name.lowercase()}_fallback"
        }
        if (read.pid !in observedPids) {
            return "pid_file_stale_pid_fallback"
        }
        if (!commandMatches) {
            return "pid_file_command_mismatch_fallback"
        }
        return "pid_file_resolved"
    }

    private fun containerPsFormat(
        containerPsAvailable: Boolean,
        processRecords: List<ContainerProcessRecord>,
        pgidSupported: Boolean,
        sidSupported: Boolean
    ): String {
        return when {
            !containerPsAvailable -> "container_ps_unavailable"
            processRecords.isEmpty() -> "container_ps_empty_needs_real_device_validation"
            pgidSupported && sidSupported -> NEW_PS_FORMAT
            else -> FALLBACK_PS_FORMAT
        }
    }

    private fun workspacePidFileMappingStatus(pidFiles: List<String>): String {
        return when {
            pidFiles.none { it.startsWith("/workspace/") || it == "/workspace" } -> "not_declared"
            pidFiles.any { RuntimeProcessUnitPidFilePathPolicy.isAllowed(it) && it.startsWith("/workspace/") } ->
                "workspace_path_declared_safe_mapping_needs_real_device_validation"
            else -> "workspace_path_declared_but_rejected"
        }
    }

    private fun runTmpPidFileMappingStatus(pidFiles: List<String>): String {
        return when {
            pidFiles.none { it.startsWith("/run/") || it.startsWith("/tmp/") } -> "not_declared"
            pidFiles.any { RuntimeProcessUnitPidFilePathPolicy.isAllowed(it) && it.startsWith("/run/") } &&
                pidFiles.any { RuntimeProcessUnitPidFilePathPolicy.isAllowed(it) && it.startsWith("/tmp/") } ->
                "run_and_tmp_declared_safe_rootfs_mapping_needs_real_device_validation"
            pidFiles.any {
                RuntimeProcessUnitPidFilePathPolicy.isAllowed(it) &&
                    (it.startsWith("/run/") || it.startsWith("/tmp/"))
            } -> "run_or_tmp_declared_safe_rootfs_mapping_needs_real_device_validation"
            else -> "run_tmp_declared_but_rejected"
        }
    }

    private fun pidFileProbeStatus(pidFiles: List<String>, unsafePidFileCount: Int): String {
        return when {
            pidFiles.isEmpty() -> "not_declared_manual_probe_required"
            unsafePidFileCount > 0 -> "unsafe_path_rejected"
            else -> "safe_pid_file_paths_declared_read_fallback_non_crashing"
        }
    }

    private fun hostPidContainerPidCorrelation(
        roots: List<RuntimeRootSnapshot>,
        source: String
    ): String {
        val observedExpected = roots.filter { it.observedPid != null && it.expectedPid != null }
        return when {
            observedExpected.any { it.observedPid == it.expectedPid } ->
                "observed_pid_matches_registered_pid"
            observedExpected.any { it.observedPid != it.expectedPid } ->
                "observed_pid_differs_from_registered_pid_needs_real_device_validation"
            source.contains("host_proc", ignoreCase = true) ->
                "host_proc_direct_pid_observation"
            roots.any { it.observedPid != null } ->
                "observed_pid_without_registered_correlation"
            else -> "unknown_needs_real_device_validation"
        }
    }

    private fun processGroupObservationStatus(
        pgidSupported: Boolean,
        sidSupported: Boolean
    ): String {
        return when {
            pgidSupported && sidSupported -> "pgid_sid_observed_dry_run_only"
            pgidSupported -> "pgid_observed_sid_missing_fallback"
            sidSupported -> "sid_observed_pgid_missing_fallback"
            else -> "pgid_sid_missing_fallback"
        }
    }

    private fun manualKillObservationStatus(roots: List<RuntimeRootSnapshot>): String {
        return when {
            roots.any {
                it.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED &&
                    it.reality == RuntimeRootReality.STALE_RECORD
            } -> "unmanaged_stale_snapshot_only_no_auto_recovery"
            roots.any { it.reality == RuntimeRootReality.STALE_RECORD } ->
                "stale_registered_root_classified_by_stop_reconciliation"
            else -> "manual_kill_requires_real_device_checklist"
        }
    }

    private fun unmanagedProcessObservationStatus(roots: List<RuntimeRootSnapshot>): String {
        return when {
            roots.any {
                it.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED &&
                    it.processUnitId == null &&
                    it.reality == RuntimeRootReality.STALE_RECORD
            } -> "disappeared_snapshot_only_no_auto_registration"
            roots.any {
                it.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED &&
                    it.processUnitId == null &&
                    it.reality == RuntimeRootReality.OBSERVED
            } -> "unmanaged_observed_only_no_auto_registration"
            else -> "not_observed"
        }
    }

    private fun memoryObservationStatus(
        rssSupported: Boolean,
        vmSizeSupported: Boolean
    ): String {
        return when {
            rssSupported && vmSizeSupported -> "rss_and_vmsize_observed"
            rssSupported -> "rss_observed_vmsize_missing"
            vmSizeSupported -> "vmsize_observed_rss_missing"
            else -> "memory_counters_missing_or_unknown"
        }
    }

    private fun warningsFor(
        containerPsAvailable: Boolean,
        pgidSupported: Boolean,
        sidSupported: Boolean,
        rssSupported: Boolean,
        vmSizeSupported: Boolean,
        pidFiles: List<String>,
        unsafePidFileCount: Int,
        roots: List<RuntimeRootSnapshot>,
        source: String
    ): List<String> {
        val warnings = linkedSetOf<String>()
        if (!containerPsAvailable) warnings += "container_ps_not_current_collection_source:$source"
        if (!pgidSupported) warnings += "pgid_missing_or_old_ps_fallback"
        if (!sidSupported) warnings += "sid_missing_or_old_ps_fallback"
        if (!rssSupported) warnings += "rss_memory_missing_or_zero"
        if (!vmSizeSupported) warnings += "vmsize_memory_missing_or_zero"
        if (pidFiles.isEmpty()) warnings += "pid_file_probe_requires_manifest_or_manual_test"
        if (unsafePidFileCount > 0) warnings += "unsafe_pid_file_path_rejected_count=$unsafePidFileCount"
        if (hostPidContainerPidCorrelation(roots, source).contains("unknown")) {
            warnings += "host_container_pid_correlation_unknown"
        }
        if (unmanagedProcessObservationStatus(roots) == "not_observed") {
            warnings += "unmanaged_manual_kill_requires_real_device_checklist"
        }
        return warnings.toList()
    }

    private fun markersFor(
        pgidSupported: Boolean,
        sidSupported: Boolean,
        pidFiles: List<String>,
        rssSupported: Boolean,
        vmSizeSupported: Boolean,
        source: String,
        roots: List<RuntimeRootSnapshot>
    ): List<String> {
        val markers = linkedSetOf<String>()
        if (!pgidSupported || !sidSupported) {
            markers += "pgid_sid_stability_under_android_proot"
        }
        if (pidFiles.isEmpty() || pidFiles.any(RuntimeProcessUnitPidFilePathPolicy::isAllowed)) {
            markers += "pid_file_mapped_path_against_real_rootfs"
        }
        markers += "ps_output_format_compatibility"
        if (hostPidContainerPidCorrelation(roots, source).contains("unknown") ||
            hostPidContainerPidCorrelation(roots, source).contains("needs_real_device_validation")
        ) {
            markers += "host_pid_container_pid_correlation"
        }
        if (!rssSupported || !vmSizeSupported) {
            markers += "memory_rss_vmsize_accuracy_under_android_proot"
        }
        markers += "future_process_group_kill_behavior_reliability"
        return markers.toList()
    }
}

private fun String?.toObservationProbeEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(320)
}
