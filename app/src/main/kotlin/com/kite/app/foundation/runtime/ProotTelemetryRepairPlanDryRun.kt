package com.kite.app.foundation.runtime

enum class ProotTelemetryRepairAction {
    NONE,
    WAIT_FOR_SOURCE,
    REVIEW_SOURCE,
    REVIEW_READER,
    REVIEW_STALE_SOURCE,
    ROTATE_HISTORY_CONTAMINATED_JSONL
}

enum class ProotTelemetryRepairReadiness {
    NOT_NEEDED,
    NOT_READY,
    MANUAL_READY
}

data class ProotTelemetryRepairPlanDryRunSnapshot(
    val mode: String = "proot_telemetry_repair_plan_dry_run_v0",
    val enforcementMode: String = "manual_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val action: ProotTelemetryRepairAction = ProotTelemetryRepairAction.NONE,
    val readiness: ProotTelemetryRepairReadiness = ProotTelemetryRepairReadiness.NOT_NEEDED,
    val manualActionRequired: Boolean = false,
    val sourcePath: String = "",
    val proposedArchivePath: String = "",
    val fileExists: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val currentTotalEvents: Long = 0L,
    val currentSkippedBytes: Long = 0L,
    val currentParseErrors: Long = 0L,
    val canRepairWithoutRuntimeRestart: Boolean = false,
    val expectedReadinessImpact: String = "none",
    val reason: String = "no_repair_needed"
) {
    fun summary(): String {
        return "mode=$mode action=$action readiness=$readiness manualRequired=$manualActionRequired " +
            "fileExists=$fileExists size=$fileSizeBytes skipped=$currentSkippedBytes " +
            "parseErrors=$currentParseErrors noRuntimeRestart=$canRepairWithoutRuntimeRestart " +
            "impact=$expectedReadinessImpact enforcement=$enforcementEnabled"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("proot_telemetry_repair_mode=${mode.toTelemetryRepairEnvValue()}")
            appendLine("proot_telemetry_repair_enforcement_mode=${enforcementMode.toTelemetryRepairEnvValue()}")
            appendLine("proot_telemetry_repair_enforcement_enabled=$enforcementEnabled")
            appendLine("proot_telemetry_repair_generated_at=$generatedAtMs")
            appendLine("proot_telemetry_repair_action=${action.name}")
            appendLine("proot_telemetry_repair_readiness=${readiness.name}")
            appendLine("proot_telemetry_repair_manual_action_required=$manualActionRequired")
            appendLine("proot_telemetry_repair_source_path=${sourcePath.toTelemetryRepairEnvValue()}")
            appendLine("proot_telemetry_repair_proposed_archive_path=${proposedArchivePath.toTelemetryRepairEnvValue()}")
            appendLine("proot_telemetry_repair_file_exists=$fileExists")
            appendLine("proot_telemetry_repair_file_size_bytes=$fileSizeBytes")
            appendLine("proot_telemetry_repair_current_total_events=$currentTotalEvents")
            appendLine("proot_telemetry_repair_current_skipped_bytes=$currentSkippedBytes")
            appendLine("proot_telemetry_repair_current_parse_errors=$currentParseErrors")
            appendLine("proot_telemetry_repair_can_repair_without_runtime_restart=$canRepairWithoutRuntimeRestart")
            appendLine("proot_telemetry_repair_expected_readiness_impact=${expectedReadinessImpact.toTelemetryRepairEnvValue()}")
            appendLine("proot_telemetry_repair_reason=${reason.toTelemetryRepairEnvValue()}")
            appendLine("proot_telemetry_repair_boundary=dry_run_manual_only_no_file_move_no_delete_no_truncate_no_reader_restart_no_canary_activation")
        }
    }
}

object ProotTelemetryRepairPlanDryRun {
    fun evaluate(
        health: ProotTelemetryHealthDryRunSnapshot,
        telemetry: ProotTelemetrySnapshot,
        now: Long = System.currentTimeMillis()
    ): ProotTelemetryRepairPlanDryRunSnapshot {
        val action = resolveAction(health)
        val readiness = resolveReadiness(action, health, telemetry)
        val sourcePath = telemetry.sourcePath.ifBlank { health.sourcePath }
        val archivePath = proposedArchivePath(sourcePath, now)
        val manualRequired = readiness == ProotTelemetryRepairReadiness.MANUAL_READY
        return ProotTelemetryRepairPlanDryRunSnapshot(
            generatedAtMs = now,
            action = action,
            readiness = readiness,
            manualActionRequired = manualRequired,
            sourcePath = sourcePath,
            proposedArchivePath = if (manualRequired) archivePath else "",
            fileExists = health.fileExists,
            fileSizeBytes = health.fileSizeBytes,
            currentTotalEvents = health.totalEvents,
            currentSkippedBytes = health.skippedBytes,
            currentParseErrors = health.parseErrors,
            canRepairWithoutRuntimeRestart = manualRequired && telemetry.collectionStatus == "loaded",
            expectedReadinessImpact = expectedImpact(action, readiness),
            reason = buildReason(action, readiness, health)
        )
    }

    private fun resolveAction(
        health: ProotTelemetryHealthDryRunSnapshot
    ): ProotTelemetryRepairAction {
        return when (health.state) {
            ProotTelemetryHealthState.NOT_STARTED -> ProotTelemetryRepairAction.WAIT_FOR_SOURCE
            ProotTelemetryHealthState.SOURCE_MISSING -> ProotTelemetryRepairAction.REVIEW_SOURCE
            ProotTelemetryHealthState.READ_ERROR -> ProotTelemetryRepairAction.REVIEW_READER
            ProotTelemetryHealthState.STALE -> ProotTelemetryRepairAction.REVIEW_STALE_SOURCE
            ProotTelemetryHealthState.HISTORY_CONTAMINATED ->
                ProotTelemetryRepairAction.ROTATE_HISTORY_CONTAMINATED_JSONL
            ProotTelemetryHealthState.CURRENTLY_QUIET,
            ProotTelemetryHealthState.CURRENTLY_HEALTHY,
            ProotTelemetryHealthState.HIGH_PRESSURE_HEALTHY -> ProotTelemetryRepairAction.NONE
        }
    }

    private fun resolveReadiness(
        action: ProotTelemetryRepairAction,
        health: ProotTelemetryHealthDryRunSnapshot,
        telemetry: ProotTelemetrySnapshot
    ): ProotTelemetryRepairReadiness {
        if (action == ProotTelemetryRepairAction.NONE) {
            return ProotTelemetryRepairReadiness.NOT_NEEDED
        }
        if (action != ProotTelemetryRepairAction.ROTATE_HISTORY_CONTAMINATED_JSONL) {
            return ProotTelemetryRepairReadiness.NOT_READY
        }
        return if (
            health.fileExists &&
            health.fileSizeBytes > 0L &&
            health.shadowUsable &&
            telemetry.collectionStatus == "loaded" &&
            health.blocker == "history_contaminated"
        ) {
            ProotTelemetryRepairReadiness.MANUAL_READY
        } else {
            ProotTelemetryRepairReadiness.NOT_READY
        }
    }

    private fun proposedArchivePath(sourcePath: String, now: Long): String {
        if (sourcePath.isBlank()) return ""
        return "$sourcePath.history-contaminated.$now"
    }

    private fun expectedImpact(
        action: ProotTelemetryRepairAction,
        readiness: ProotTelemetryRepairReadiness
    ): String {
        return when {
            action == ProotTelemetryRepairAction.NONE -> "none"
            readiness == ProotTelemetryRepairReadiness.MANUAL_READY ->
                "next_clean_jsonl_refresh_should_clear_history_contaminated_blocker"
            else -> "diagnose_source_before_repair"
        }
    }

    private fun buildReason(
        action: ProotTelemetryRepairAction,
        readiness: ProotTelemetryRepairReadiness,
        health: ProotTelemetryHealthDryRunSnapshot
    ): String {
        return "action=${action.name},readiness=${readiness.name},health=${health.state.name}," +
            "blocker=${health.blocker},skippedBytes=${health.skippedBytes},parseErrors=${health.parseErrors}"
    }
}

private fun String?.toTelemetryRepairEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
