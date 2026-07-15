package com.kite.app.foundation.runtime

/*
 * T11:从 ProotTelemetryStore.kt 抽出的纯 model(5 enum + 14 data class)。
 * 同包,无需相互 import。抽出后 ProotTelemetryStore.kt 从 1803 行降至约 1360 行。
 */

import org.json.JSONObject

enum class ProotTelemetryEventType {
    ProotTelemetryStarted,
    TraceeCreated,
    ForkDetected,
    CloneDetected,
    VforkDetected,
    ExecDetected,
    TraceeExited,
    TraceeSignaled,
    Unknown
}

data class ProotTelemetryEvent(
    val eventType: ProotTelemetryEventType,
    val timestampMs: Long,
    val telemetrySessionId: String = "",
    val prootStartMs: Long = 0L,
    val prootPid: Int,
    val traceePid: Int,
    val traceeVpid: Long,
    val processGroupId: Int?,
    val sessionId: Int?,
    val parentTraceePid: Int?,
    val parentTraceeVpid: Long?,
    val flags: Long,
    val sourceHook: String,
    val costLevel: String,
    val executable: String = "",
    val argvHash: String = "",
    val argvPreview: String = "",
    val cwd: String = "",
    val kfRuntimeId: String = "",
    val kfUnitId: String = "",
    val exitCode: Int? = null,
    val signal: Int? = null
)

data class ProotTelemetryCounters(
    val totalEvents: Long = 0L,
    val prootStarted: Long = 0L,
    val traceeCreated: Long = 0L,
    val forkDetected: Long = 0L,
    val cloneDetected: Long = 0L,
    val vforkDetected: Long = 0L,
    val execDetected: Long = 0L,
    val traceeExited: Long = 0L,
    val traceeSignaled: Long = 0L,
    val unknownEvents: Long = 0L,
    val parseErrors: Long = 0L,
    val skippedBytes: Long = 0L
)

enum class ProotLiveProcessState {
    RUNNING,
    EXITED,
    SIGNALED,
    UNKNOWN
}

data class ProotTraceeRecord(
    val traceePid: Int,
    val traceeVpid: Long,
    val telemetrySessionId: String = "",
    val prootStartMs: Long = 0L,
    val prootPid: Int,
    val processGroupId: Int? = null,
    val sessionId: Int? = null,
    val parentTraceePid: Int?,
    val parentTraceeVpid: Long?,
    val createdAtMs: Long,
    val lastEventAtMs: Long,
    val lastEventType: ProotTelemetryEventType,
    val lastSourceHook: String = "unknown",
    val lastCostLevel: String = "lifecycle_low",
    val executable: String = "",
    val argvHash: String = "",
    val argvPreview: String = "",
    val cwd: String = "",
    val kfRuntimeId: String = "",
    val kfUnitId: String = "",
    val execCount: Int = 0,
    val childEventCount: Int = 0,
    val exitedAtMs: Long? = null,
    val signaledAtMs: Long? = null,
    val exitCode: Int? = null,
    val signal: Int? = null
) {
    val lifecycleId: String
        get() = prootTraceeLifecycleId(
            telemetrySessionId = telemetrySessionId,
            prootStartMs = prootStartMs,
            prootPid = prootPid,
            traceePid = traceePid,
            legacyCreatedAtMs = createdAtMs
        )

    val running: Boolean
        get() = exitedAtMs == null && signaledAtMs == null

    val liveState: ProotLiveProcessState
        get() = when {
            signaledAtMs != null -> ProotLiveProcessState.SIGNALED
            exitedAtMs != null -> ProotLiveProcessState.EXITED
            running -> ProotLiveProcessState.RUNNING
            else -> ProotLiveProcessState.UNKNOWN
        }
}

data class ProotLiveProcessEntry(
    val prootPid: Int,
    val telemetrySessionId: String = "",
    val prootStartMs: Long = 0L,
    val traceePid: Int,
    val traceeVpid: Long,
    val processGroupId: Int? = null,
    val sessionId: Int? = null,
    val parentTraceePid: Int?,
    val parentTraceeVpid: Long?,
    val state: ProotLiveProcessState,
    val createdAtMs: Long,
    val lastSeenAtMs: Long,
    val exitedAtMs: Long?,
    val signaledAtMs: Long?,
    val lastEventType: ProotTelemetryEventType,
    val lastSourceHook: String,
    val lastCostLevel: String,
    val executable: String = "",
    val argvHash: String = "",
    val argvPreview: String = "",
    val cwd: String = "",
    val kfRuntimeId: String = "",
    val kfUnitId: String = "",
    val execCount: Int,
    val childEventCount: Int,
    val exitCode: Int? = null,
    val signal: Int? = null
) {
    val lifecycleId: String
        get() = prootTraceeLifecycleId(
            telemetrySessionId = telemetrySessionId,
            prootStartMs = prootStartMs,
            prootPid = prootPid,
            traceePid = traceePid,
            legacyCreatedAtMs = createdAtMs
        )
}

data class ProotProcessLiveTable(
    val mode: String = "telemetry_process_live_table_v2",
    val generatedAtMs: Long = 0L,
    val sourceStatus: String = "not_started",
    val sourcePath: String = "",
    val retentionMode: String = "running_plus_bounded_terminal_lifecycle_v2",
    val terminalRetentionMaxEntries: Int = 5_000,
    val terminalRetentionTtlMs: Long = 7L * 24L * 60L * 60L * 1000L,
    val liveTraceeCount: Int = 0,
    val knownTraceeCount: Int = 0,
    val exitedTraceeCount: Int = 0,
    val signaledTraceeCount: Int = 0,
    val entries: List<ProotLiveProcessEntry> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode status=$sourceStatus live=$liveTraceeCount known=$knownTraceeCount " +
            "exited=$exitedTraceeCount signaled=$signaledTraceeCount"
    }
}

data class ProotOwnerProcessGroup(
    val ownerId: String,
    val unitIds: List<String> = emptyList(),
    val prootPids: List<Int> = emptyList(),
    val telemetrySessionIds: List<String> = emptyList(),
    val traceePids: List<Int> = emptyList(),
    val processGroupIds: List<Int> = emptyList(),
    val sessionIds: List<Int> = emptyList(),
    val liveTraceeCount: Int = 0,
    val lastSeenAtMs: Long = 0L
)

data class ProotOwnerProcessIndex(
    val mode: String = "telemetry_owner_process_index_v1",
    val generatedAtMs: Long = 0L,
    val sourceStatus: String = "not_started",
    val ownerCount: Int = 0,
    val liveTraceeCount: Int = 0,
    val groups: List<ProotOwnerProcessGroup> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode status=$sourceStatus owners=$ownerCount live=$liveTraceeCount"
    }
}

private fun prootTraceeLifecycleId(
    telemetrySessionId: String,
    prootStartMs: Long,
    prootPid: Int,
    traceePid: Int,
    legacyCreatedAtMs: Long
): String {
    val explicit = telemetrySessionId.isNotBlank() || prootStartMs > 0L
    val generation = if (explicit) 0L else legacyCreatedAtMs
    return listOf(
        telemetrySessionId.ifBlank { "legacy" },
        prootStartMs,
        prootPid,
        traceePid,
        generation
    ).joinToString(":")
}

enum class ProotPressureSignalLevel {
    QUIET,
    NORMAL,
    BUSY,
    BURST
}

data class ProotPressureWindow(
    val mode: String = "telemetry_pressure_window_v0",
    val generatedAtMs: Long = 0L,
    val windowMs: Long = 60_000L,
    val eventsInWindow: Int = 0,
    val forkExecEventsInWindow: Int = 0,
    val exitEventsInWindow: Int = 0,
    val liveTraceeCount: Int = 0,
    val pressureScore: Int = 0,
    val signalLevel: ProotPressureSignalLevel = ProotPressureSignalLevel.QUIET
) {
    fun summary(): String {
        return "mode=$mode level=$signalLevel score=$pressureScore windowMs=$windowMs " +
            "events=$eventsInWindow forkExec=$forkExecEventsInWindow live=$liveTraceeCount"
    }
}

data class ProotTelemetrySnapshot(
    val mode: String = "debug_jsonl_lifecycle_v0",
    val sourcePath: String = "",
    val collectionStatus: String = "not_started",
    val fileExists: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val fileLastModifiedMs: Long = 0L,
    val refreshedAtMs: Long = 0L,
    val lastEventAtMs: Long = 0L,
    val lastReadOffsetBytes: Long = 0L,
    val ownerEvidenceCompleteFromMs: Long = 0L,
    val ownerEvidenceCoverageReason: String = "full_history",
    val lastRefreshEvents: Int = 0,
    val lastRefreshForkExecEvents: Int = 0,
    val probeDeclaredTargetLiveTracees: Int = 0,
    val liveTraceeCount: Int = 0,
    val knownTraceeCount: Int = 0,
    val counters: ProotTelemetryCounters = ProotTelemetryCounters(),
    val recentEvents: List<ProotTelemetryEvent> = emptyList(),
    val tracees: List<ProotTraceeRecord> = emptyList(),
    val processLiveTable: ProotProcessLiveTable = ProotProcessLiveTable(),
    val ownerProcessIndex: ProotOwnerProcessIndex = ProotOwnerProcessIndex(),
    val pressureWindow: ProotPressureWindow = ProotPressureWindow()
) {
    fun summary(): String {
        return "mode=$mode status=$collectionStatus events=${counters.totalEvents} " +
            "live=$liveTraceeCount known=$knownTraceeCount forkExecLast=$lastRefreshForkExecEvents " +
            "pressure=${pressureWindow.signalLevel}/${pressureWindow.pressureScore} " +
            "ownerEvidenceFrom=$ownerEvidenceCompleteFromMs " +
            "parseErrors=${counters.parseErrors}"
    }
}

enum class ProotTelemetryRepairExecutionStatus {
    ROTATED,
    NOT_READY,
    SOURCE_MISSING,
    ARCHIVE_EXISTS,
    ROTATE_FAILED
}

data class ProotTelemetryRepairExecutionResult(
    val status: ProotTelemetryRepairExecutionStatus,
    val action: ProotTelemetryRepairAction,
    val reason: String,
    val sourcePath: String,
    val archivePath: String = "",
    val rotatedBytes: Long = 0L,
    val previousTotalEvents: Long = 0L,
    val previousSkippedBytes: Long = 0L,
    val previousParseErrors: Long = 0L,
    val generatedAtMs: Long = System.currentTimeMillis()
) {
    val rotated: Boolean
        get() = status == ProotTelemetryRepairExecutionStatus.ROTATED

    fun summary(): String {
        return "status=$status action=$action rotated=$rotated source=$sourcePath archive=$archivePath " +
            "bytes=$rotatedBytes events=$previousTotalEvents skipped=$previousSkippedBytes " +
            "parseErrors=$previousParseErrors reason=$reason"
    }

    fun toLogBlock(): String {
        return buildString {
            appendLine("== rotate-proot-telemetry ==")
            appendLine("generatedAtMs=$generatedAtMs")
            appendLine("status=$status")
            appendLine("action=$action")
            appendLine("rotated=$rotated")
            appendLine("sourcePath=$sourcePath")
            appendLine("archivePath=${archivePath.ifBlank { "none" }}")
            appendLine("rotatedBytes=$rotatedBytes")
            appendLine("previousTotalEvents=$previousTotalEvents")
            appendLine("previousSkippedBytes=$previousSkippedBytes")
            appendLine("previousParseErrors=$previousParseErrors")
            appendLine("reason=$reason")
        }
    }
}

data class ProotTelemetryProbePrepareExecutionResult(
    val status: String,
    val action: String,
    val reason: String,
    val targetLiveTracees: Int,
    val sourcePath: String,
    val previousLiveTracees: Int,
    val previousPressureScore: Int,
    val previousTotalEvents: Long,
    val generatedAtMs: Long = System.currentTimeMillis()
) {
    fun summary(): String {
        return "status=$status action=$action target=$targetLiveTracees source=$sourcePath " +
            "previousLive=$previousLiveTracees previousScore=$previousPressureScore reason=$reason"
    }

    fun toLogBlock(): String {
        return buildString {
            appendLine("== prepare-proot-live-tracee-probe ==")
            appendLine("generatedAtMs=$generatedAtMs")
            appendLine("status=$status")
            appendLine("action=$action")
            appendLine("targetLiveTracees=$targetLiveTracees")
            appendLine("sourcePath=$sourcePath")
            appendLine("previousLiveTracees=$previousLiveTracees")
            appendLine("previousPressureScore=$previousPressureScore")
            appendLine("previousTotalEvents=$previousTotalEvents")
            appendLine("boundary=debug_android_control_plane_no_ubuntu_kill_no_workspace_delete")
            appendLine("reason=$reason")
        }
    }
}

data class ProotTelemetryProbeInjectExecutionResult(
    val status: String,
    val action: String,
    val reason: String,
    val targetLiveTracees: Int,
    val sourcePath: String,
    val previousLiveTracees: Int,
    val previousPressureScore: Int,
    val previousEventsInWindow: Int,
    val previousForkExecEventsInWindow: Int,
    val observedLiveTracees: Int = 0,
    val observedPressureScore: Int = 0,
    val observedEventsInWindow: Int = 0,
    val observedForkExecEventsInWindow: Int = 0,
    val generatedAtMs: Long = System.currentTimeMillis()
) {
    fun summary(): String {
        return "status=$status action=$action target=$targetLiveTracees source=$sourcePath " +
            "beforeLive=$previousLiveTracees beforeScore=$previousPressureScore " +
            "observedLive=$observedLiveTracees observedScore=$observedPressureScore reason=$reason"
    }

    fun toLogBlock(): String {
        return buildString {
            appendLine("== inject-proot-live-tracee-probe ==")
            appendLine("generatedAtMs=$generatedAtMs")
            appendLine("status=$status")
            appendLine("action=$action")
            appendLine("targetLiveTracees=$targetLiveTracees")
            appendLine("sourcePath=$sourcePath")
            appendLine("previousLiveTracees=$previousLiveTracees")
            appendLine("previousPressureScore=$previousPressureScore")
            appendLine("previousEventsInWindow=$previousEventsInWindow")
            appendLine("previousForkExecEventsInWindow=$previousForkExecEventsInWindow")
            appendLine("observedLiveTracees=$observedLiveTracees")
            appendLine("observedPressureScore=$observedPressureScore")
            appendLine("observedEventsInWindow=$observedEventsInWindow")
            appendLine("observedForkExecEventsInWindow=$observedForkExecEventsInWindow")
            appendLine("boundary=debug_android_control_plane_no_ubuntu_kill_no_workspace_delete_no_direct_proot_control")
            appendLine("reason=$reason")
        }
    }
}

data class ProotTelemetryHeartbeatExecutionResult(
    val status: String,
    val action: String,
    val reason: String,
    val sourcePath: String,
    val previousLiveTracees: Int,
    val previousPressureScore: Int,
    val previousEventsInWindow: Int,
    val previousForkExecEventsInWindow: Int,
    val observedLiveTracees: Int = 0,
    val observedPressureScore: Int = 0,
    val observedEventsInWindow: Int = 0,
    val observedForkExecEventsInWindow: Int = 0,
    val generatedAtMs: Long = System.currentTimeMillis()
) {
    fun summary(): String {
        return "status=$status action=$action source=$sourcePath " +
            "beforeLive=$previousLiveTracees beforeScore=$previousPressureScore " +
            "observedLive=$observedLiveTracees observedScore=$observedPressureScore reason=$reason"
    }

    fun toLogBlock(): String {
        return buildString {
            appendLine("== refresh-proot-telemetry-heartbeat ==")
            appendLine("generatedAtMs=$generatedAtMs")
            appendLine("status=$status")
            appendLine("action=$action")
            appendLine("sourcePath=$sourcePath")
            appendLine("previousLiveTracees=$previousLiveTracees")
            appendLine("previousPressureScore=$previousPressureScore")
            appendLine("previousEventsInWindow=$previousEventsInWindow")
            appendLine("previousForkExecEventsInWindow=$previousForkExecEventsInWindow")
            appendLine("observedLiveTracees=$observedLiveTracees")
            appendLine("observedPressureScore=$observedPressureScore")
            appendLine("observedEventsInWindow=$observedEventsInWindow")
            appendLine("observedForkExecEventsInWindow=$observedForkExecEventsInWindow")
            appendLine("boundary=debug_android_control_plane_no_tracee_no_probe_no_file_reset_no_kill_no_restart")
            appendLine("reason=$reason")
        }
    }
}

data class ProotTelemetryOwnerRetireResult(
    val status: String,
    val action: String,
    val ownerId: String,
    val reason: String,
    val sourcePath: String,
    val retiredTraceePids: List<Int> = emptyList(),
    val previousLiveTracees: Int = 0,
    val observedLiveTracees: Int = 0,
    val generatedAtMs: Long = System.currentTimeMillis()
) {
    val retired: Boolean
        get() = retiredTraceePids.isNotEmpty()

    fun summary(): String {
        return "status=$status action=$action owner=$ownerId retired=$retired " +
            "pids=${retiredTraceePids.joinToString(",")} beforeLive=$previousLiveTracees " +
            "observedLive=$observedLiveTracees reason=$reason"
    }

    fun toLogBlock(): String {
        return buildString {
            appendLine("== retire-proot-owner-tracees ==")
            appendLine("generatedAtMs=$generatedAtMs")
            appendLine("status=$status")
            appendLine("action=$action")
            appendLine("ownerId=$ownerId")
            appendLine("retired=$retired")
            appendLine("retiredTraceePids=${retiredTraceePids.joinToString(",")}")
            appendLine("sourcePath=$sourcePath")
            appendLine("previousLiveTracees=$previousLiveTracees")
            appendLine("observedLiveTracees=$observedLiveTracees")
            appendLine("boundary=android_control_plane_owner_tombstone_same_telemetry_source_no_proc_scan")
            appendLine("reason=$reason")
        }
    }
}
