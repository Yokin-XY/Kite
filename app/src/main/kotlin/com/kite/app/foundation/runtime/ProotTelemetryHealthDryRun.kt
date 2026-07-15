package com.kite.app.foundation.runtime

enum class ProotTelemetryHealthState {
    NOT_STARTED,
    SOURCE_MISSING,
    READ_ERROR,
    STALE,
    HISTORY_CONTAMINATED,
    CURRENTLY_QUIET,
    CURRENTLY_HEALTHY,
    HIGH_PRESSURE_HEALTHY
}

enum class ProotTelemetryHealthRecommendation {
    WAIT_FOR_SOURCE,
    REVIEW_SOURCE_PATH,
    REVIEW_READER_ERROR,
    REVIEW_STALE_SOURCE,
    RESET_OR_ROTATE_TELEMETRY,
    OBSERVE_ONLY,
    REVIEW_PRESSURE_BURST
}

data class ProotTelemetryHealthDryRunSnapshot(
    val mode: String = "proot_telemetry_health_dry_run_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: ProotTelemetryHealthState = ProotTelemetryHealthState.NOT_STARTED,
    val recommendation: ProotTelemetryHealthRecommendation = ProotTelemetryHealthRecommendation.WAIT_FOR_SOURCE,
    val canaryHealthy: Boolean = false,
    val shadowUsable: Boolean = false,
    val sourceStatus: String = "not_started",
    val sourcePath: String = "",
    val fileExists: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val fileAgeMs: Long? = null,
    val lastEventAgeMs: Long? = null,
    val refreshedAgeMs: Long? = null,
    val totalEvents: Long = 0L,
    val lastRefreshEvents: Int = 0,
    val parseErrors: Long = 0L,
    val skippedBytes: Long = 0L,
    val pressureLevel: ProotPressureSignalLevel = ProotPressureSignalLevel.QUIET,
    val pressureScore: Int = 0,
    val blocker: String = "not_started",
    val reason: String = "waiting_for_telemetry"
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation canaryHealthy=$canaryHealthy " +
            "shadowUsable=$shadowUsable status=$sourceStatus events=$totalEvents parseErrors=$parseErrors " +
            "skippedBytes=$skippedBytes pressure=$pressureLevel/$pressureScore blocker=$blocker " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("proot_telemetry_health_mode=${mode.toTelemetryHealthEnvValue()}")
            appendLine("proot_telemetry_health_enforcement_mode=${enforcementMode.toTelemetryHealthEnvValue()}")
            appendLine("proot_telemetry_health_enforcement_enabled=$enforcementEnabled")
            appendLine("proot_telemetry_health_generated_at=$generatedAtMs")
            appendLine("proot_telemetry_health_state=${state.name}")
            appendLine("proot_telemetry_health_recommendation=${recommendation.name}")
            appendLine("proot_telemetry_health_canary_healthy=$canaryHealthy")
            appendLine("proot_telemetry_health_shadow_usable=$shadowUsable")
            appendLine("proot_telemetry_health_source_status=${sourceStatus.toTelemetryHealthEnvValue()}")
            appendLine("proot_telemetry_health_source_path=${sourcePath.toTelemetryHealthEnvValue()}")
            appendLine("proot_telemetry_health_file_exists=$fileExists")
            appendLine("proot_telemetry_health_file_size_bytes=$fileSizeBytes")
            appendLine("proot_telemetry_health_file_age_ms=${fileAgeMs ?: -1L}")
            appendLine("proot_telemetry_health_last_event_age_ms=${lastEventAgeMs ?: -1L}")
            appendLine("proot_telemetry_health_refreshed_age_ms=${refreshedAgeMs ?: -1L}")
            appendLine("proot_telemetry_health_total_events=$totalEvents")
            appendLine("proot_telemetry_health_last_refresh_events=$lastRefreshEvents")
            appendLine("proot_telemetry_health_parse_errors=$parseErrors")
            appendLine("proot_telemetry_health_skipped_bytes=$skippedBytes")
            appendLine("proot_telemetry_health_pressure_level=${pressureLevel.name}")
            appendLine("proot_telemetry_health_pressure_score=$pressureScore")
            appendLine("proot_telemetry_health_blocker=${blocker.toTelemetryHealthEnvValue()}")
            appendLine("proot_telemetry_health_reason=${reason.toTelemetryHealthEnvValue()}")
            appendLine("proot_telemetry_health_boundary=dry_run_no_file_reset_no_rotation_no_reader_restart_no_canary_activation")
        }
    }
}

object ProotTelemetryHealthDryRun {
    private const val STALE_SOURCE_MS = 30_000L
    private const val STALE_REFRESH_MS = 10_000L

    fun evaluate(
        telemetry: ProotTelemetrySnapshot,
        now: Long = System.currentTimeMillis()
    ): ProotTelemetryHealthDryRunSnapshot {
        val fileAgeMs = telemetry.fileLastModifiedMs.toAgeMs(now)
        val lastEventAgeMs = telemetry.lastEventAtMs.toAgeMs(now)
        val refreshedAgeMs = telemetry.refreshedAtMs.toAgeMs(now)
        val stateAndReason = resolveState(
            telemetry = telemetry,
            fileAgeMs = fileAgeMs,
            refreshedAgeMs = refreshedAgeMs
        )
        val state = stateAndReason.first
        val canaryHealthy = state == ProotTelemetryHealthState.CURRENTLY_HEALTHY ||
            state == ProotTelemetryHealthState.CURRENTLY_QUIET ||
            state == ProotTelemetryHealthState.HIGH_PRESSURE_HEALTHY
        val shadowUsable = telemetry.collectionStatus == "loaded" &&
            telemetry.fileExists &&
            telemetry.counters.totalEvents > 0L

        return ProotTelemetryHealthDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = state.toRecommendation(),
            canaryHealthy = canaryHealthy,
            shadowUsable = shadowUsable,
            sourceStatus = telemetry.collectionStatus,
            sourcePath = telemetry.sourcePath,
            fileExists = telemetry.fileExists,
            fileSizeBytes = telemetry.fileSizeBytes,
            fileAgeMs = fileAgeMs,
            lastEventAgeMs = lastEventAgeMs,
            refreshedAgeMs = refreshedAgeMs,
            totalEvents = telemetry.counters.totalEvents,
            lastRefreshEvents = telemetry.lastRefreshEvents,
            parseErrors = telemetry.counters.parseErrors,
            skippedBytes = telemetry.counters.skippedBytes,
            pressureLevel = telemetry.pressureWindow.signalLevel,
            pressureScore = telemetry.pressureWindow.pressureScore,
            blocker = state.toBlocker(),
            reason = stateAndReason.second
        )
    }

    private fun resolveState(
        telemetry: ProotTelemetrySnapshot,
        fileAgeMs: Long?,
        refreshedAgeMs: Long?
    ): Pair<ProotTelemetryHealthState, String> {
        if (telemetry.collectionStatus == "not_started") {
            return ProotTelemetryHealthState.NOT_STARTED to "reader_not_started"
        }
        if (!telemetry.fileExists || telemetry.collectionStatus == "file_missing") {
            return ProotTelemetryHealthState.SOURCE_MISSING to "telemetry_file_missing"
        }
        if (telemetry.collectionStatus.startsWith("read_error:")) {
            return ProotTelemetryHealthState.READ_ERROR to telemetry.collectionStatus
        }
        if (telemetry.collectionStatus != "loaded") {
            return ProotTelemetryHealthState.NOT_STARTED to "status_${telemetry.collectionStatus}"
        }
        if (refreshedAgeMs != null && refreshedAgeMs > STALE_REFRESH_MS) {
            return ProotTelemetryHealthState.STALE to "reader_refresh_stale"
        }
        if (telemetry.counters.parseErrors > 0L) {
            return ProotTelemetryHealthState.HISTORY_CONTAMINATED to
                "parseErrors=${telemetry.counters.parseErrors}"
        }
        if (telemetry.counters.totalEvents <= 0L) {
            return ProotTelemetryHealthState.CURRENTLY_QUIET to "loaded_waiting_for_first_event"
        }
        if (fileAgeMs != null && fileAgeMs > STALE_SOURCE_MS) {
            return ProotTelemetryHealthState.STALE to "telemetry_file_stale"
        }
        if (telemetry.pressureWindow.signalLevel == ProotPressureSignalLevel.BURST) {
            return ProotTelemetryHealthState.HIGH_PRESSURE_HEALTHY to "telemetry_valid_but_pressure_burst"
        }
        return ProotTelemetryHealthState.CURRENTLY_HEALTHY to if (
            telemetry.ownerEvidenceCompleteFromMs > 0L
        ) {
            "telemetry_valid_owner_evidence_from_${telemetry.ownerEvidenceCompleteFromMs}"
        } else {
            "telemetry_valid"
        }
    }

    private fun ProotTelemetryHealthState.toRecommendation(): ProotTelemetryHealthRecommendation {
        return when (this) {
            ProotTelemetryHealthState.NOT_STARTED -> ProotTelemetryHealthRecommendation.WAIT_FOR_SOURCE
            ProotTelemetryHealthState.SOURCE_MISSING -> ProotTelemetryHealthRecommendation.REVIEW_SOURCE_PATH
            ProotTelemetryHealthState.READ_ERROR -> ProotTelemetryHealthRecommendation.REVIEW_READER_ERROR
            ProotTelemetryHealthState.STALE -> ProotTelemetryHealthRecommendation.REVIEW_STALE_SOURCE
            ProotTelemetryHealthState.HISTORY_CONTAMINATED ->
                ProotTelemetryHealthRecommendation.RESET_OR_ROTATE_TELEMETRY
            ProotTelemetryHealthState.CURRENTLY_QUIET,
            ProotTelemetryHealthState.CURRENTLY_HEALTHY -> ProotTelemetryHealthRecommendation.OBSERVE_ONLY
            ProotTelemetryHealthState.HIGH_PRESSURE_HEALTHY ->
                ProotTelemetryHealthRecommendation.REVIEW_PRESSURE_BURST
        }
    }

    private fun ProotTelemetryHealthState.toBlocker(): String {
        return when (this) {
            ProotTelemetryHealthState.NOT_STARTED -> "reader_not_started"
            ProotTelemetryHealthState.SOURCE_MISSING -> "source_missing"
            ProotTelemetryHealthState.READ_ERROR -> "read_error"
            ProotTelemetryHealthState.STALE -> "stale_source"
            ProotTelemetryHealthState.HISTORY_CONTAMINATED -> "history_contaminated"
            ProotTelemetryHealthState.CURRENTLY_QUIET,
            ProotTelemetryHealthState.CURRENTLY_HEALTHY,
            ProotTelemetryHealthState.HIGH_PRESSURE_HEALTHY -> "none"
        }
    }

    private fun Long.toAgeMs(now: Long): Long? {
        if (this <= 0L) return null
        return (now - this).coerceAtLeast(0L)
    }
}

private fun String?.toTelemetryHealthEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(220)
}
