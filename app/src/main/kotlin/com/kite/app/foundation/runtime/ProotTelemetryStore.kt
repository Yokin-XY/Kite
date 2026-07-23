package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.logging.Logger
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

object ProotTelemetryStore {
    private const val LOG_TAG = "ProotTelemetryStore"
    private const val TELEMETRY_FILE_NAME = "kf-proot-telemetry.jsonl"
    private const val MAX_INCREMENTAL_READ_BYTES = 256 * 1024
    private const val TERMINAL_TRACEE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    private const val ROTATED_TELEMETRY_SEGMENTS = 3
    private const val ROTATED_BASELINE_READ_BYTES = MAX_INCREMENTAL_READ_BYTES
    private const val ROTATED_BASELINE_EVENT_TTL_MS = TERMINAL_TRACEE_TTL_MS
    private const val ACTIVE_REGISTRY_SESSION_CREATION_GRACE_MS = 2_000L
    private const val MAX_RECENT_EVENTS = 128
    private const val MAX_TERMINAL_TRACEE_RECORDS = 5_000
    private const val PRESSURE_WINDOW_MS = 60_000L
    private const val SYNTHETIC_PROBE_EVENT_AGE_MS = PRESSURE_WINDOW_MS + 5_000L
    private const val AUTO_REFRESH_INTERVAL_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(ProotTelemetrySnapshot())
    val snapshot: StateFlow<ProotTelemetrySnapshot> = _snapshot

    @Volatile
    private var refreshJob: Job? = null

    @Volatile
    private var autoRefreshJob: Job? = null

    @Volatile
    private var pendingRefresh = false

    private val readerLock = Any()
    private val lock = Any()
    private var lastPath: String = ""
    private var lastOffsetBytes: Long = 0L
    private var rotationBaselineLoadedForPath: String = ""
    private var readerEpochMs: Long = System.currentTimeMillis()
    private var ownerEvidenceCompleteFromMs: Long = readerEpochMs
    private var ownerEvidenceCoverageReason: String = "reader_initializing"
    private var pendingPartialLine: String = ""
    private var probeDeclaredTargetLiveTracees: Int = 0
    private var counters = ProotTelemetryCounters()
    private val recentEvents = ArrayDeque<ProotTelemetryEvent>()
    private val tracees = LinkedHashMap<TraceeLifecycleKey, ProotTraceeRecord>()
    private val eventContinuity = ProotEventContinuityTracker()
    private val processVerifier = ProotProcessVerifier()
    private var activeRegistryRecoveryPending = true
    private var latestActiveRegistry = ProotActiveRegistrySnapshot(
        status = ProotActiveRegistryReadStatus.MISSING,
        rootPath = "",
    )
    private var activeRegistryReconciledAtMs = 0L

    fun startAutoRefresh(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            if (autoRefreshJob?.isActive == true) {
                return
            }
            autoRefreshJob = scope.launch {
                while (true) {
                    refresh(appContext)
                    delay(AUTO_REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            val running = refreshJob
            if (running != null && running.isActive) {
                pendingRefresh = true
                return
            }
            refreshJob = scope.launch {
                try {
                    do {
                        clearPendingRefresh()
                        readTelemetry(appContext)
                    } while (consumePendingRefresh())
                } finally {
                    synchronized(this@ProotTelemetryStore) {
                        refreshJob = null
                    }
                }
            }
        }
    }

    fun refreshBlocking(context: Context): ProotTelemetrySnapshot {
        val appContext = context.applicationContext
        readTelemetry(appContext)
        return snapshot.value
    }

    /**
     * 只核验调用方已经持有的稳定引用，并把核验结果合并回同一份运行事实。
     * 该入口不会枚举 `/proc`，成本与目标数量线性相关。
     */
    fun verifyProcessRefs(
        context: Context,
        refs: Collection<ProotProcessRef>,
        refreshFirst: Boolean = true,
    ): List<ProotProcessVerification> {
        val appContext = context.applicationContext
        val unique = refs
            .filter { it.hostPid > 1 }
            .distinctBy(ProotProcessRef::lifecycleId)
        if (unique.isEmpty()) return emptyList()
        if (refreshFirst) readTelemetry(appContext)
        val results = unique.map(processVerifier::verify)
        applyProcessVerifications(appContext, results)
        return results
    }

    /** 接收控制后端刚刚完成的定向核验，避免为了更新 UI 再做一次探测。 */
    fun applyProcessVerifications(
        context: Context,
        results: Collection<ProotProcessVerification>,
    ) {
        if (results.isEmpty()) return
        val appContext = context.applicationContext
        synchronized(readerLock) {
            val now = System.currentTimeMillis()
            results.forEach { verification ->
                val key = tracees.keys.firstOrNull { it.matches(verification.ref) } ?: return@forEach
                val record = tracees[key] ?: return@forEach
                val terminal = verification.terminal ||
                    verification.status == ProotProcessVerificationStatus.MATCHED_ZOMBIE
                tracees[key] = record.copy(
                    processGroupId = verification.processGroupId ?: record.processGroupId,
                    sessionId = verification.sessionId ?: record.sessionId,
                    parentTraceePid = verification.parentPid ?: record.parentTraceePid,
                    lastEventAtMs = if (terminal) now else record.lastEventAtMs,
                    lastSourceHook = if (terminal) {
                        "android_targeted_identity_terminal"
                    } else {
                        record.lastSourceHook
                    },
                    exitedAtMs = if (terminal) now else record.exitedAtMs,
                    kernelState = verification.kernelState,
                    verificationStatus = verification.status,
                    verifiedAtElapsedMs = verification.verifiedAtElapsedMs,
                    evidenceSource = "event+android_targeted_probe",
                )
            }
            trimTracees()
            val current = _snapshot.value
            _snapshot.value = buildSnapshot(
                file = telemetryFile(appContext),
                collectionStatus = current.collectionStatus,
                lastRefreshEvents = 0,
                lastRefreshForkExecEvents = 0,
            )
        }
    }

    fun rotateHistoryContaminatedJsonl(
        context: Context,
        reason: String = "manual-repair"
    ): ProotTelemetryRepairExecutionResult {
        val appContext = context.applicationContext
        runCatching { readTelemetry(appContext) }
            .onFailure { error ->
                Logger.e(LOG_TAG, "manual telemetry refresh before repair failed: ${error.message}")
            }

        val file = telemetryFile(appContext)
        val before = snapshot.value
        val health = ProotTelemetryHealthDryRun.evaluate(before)
        val plan = ProotTelemetryRepairPlanDryRun.evaluate(
            health = health,
            telemetry = before
        )
        if (
            plan.action != ProotTelemetryRepairAction.ROTATE_HISTORY_CONTAMINATED_JSONL ||
            plan.readiness != ProotTelemetryRepairReadiness.MANUAL_READY ||
            health.blocker != "history_contaminated"
        ) {
            return ProotTelemetryRepairExecutionResult(
                status = ProotTelemetryRepairExecutionStatus.NOT_READY,
                action = plan.action,
                reason = "manual_repair_not_ready:$reason:${plan.reason}",
                sourcePath = file.absolutePath,
                previousTotalEvents = before.counters.totalEvents,
                previousSkippedBytes = before.counters.skippedBytes,
                previousParseErrors = before.counters.parseErrors
            )
        }
        if (!file.exists()) {
            return ProotTelemetryRepairExecutionResult(
                status = ProotTelemetryRepairExecutionStatus.SOURCE_MISSING,
                action = plan.action,
                reason = "telemetry_source_missing:$reason",
                sourcePath = file.absolutePath,
                previousTotalEvents = before.counters.totalEvents,
                previousSkippedBytes = before.counters.skippedBytes,
                previousParseErrors = before.counters.parseErrors
            )
        }

        val archiveFile = uniqueHistoryArchiveFile(file, System.currentTimeMillis())
        if (archiveFile.exists()) {
            return ProotTelemetryRepairExecutionResult(
                status = ProotTelemetryRepairExecutionStatus.ARCHIVE_EXISTS,
                action = plan.action,
                reason = "archive_already_exists:$reason",
                sourcePath = file.absolutePath,
                archivePath = archiveFile.absolutePath,
                previousTotalEvents = before.counters.totalEvents,
                previousSkippedBytes = before.counters.skippedBytes,
                previousParseErrors = before.counters.parseErrors
            )
        }

        val rotatedBytes = file.length()
        val rotated = synchronized(lock) {
            runCatching {
                if (!file.exists()) return@runCatching false
                // Native telemetry keeps an O_APPEND fd open; truncate keeps that fd on a clean source file.
                file.copyTo(archiveFile, overwrite = false)
                RandomAccessFile(file, "rw").use { raf ->
                    raf.setLength(0L)
                }
                resetReaderState(file.absolutePath)
                _snapshot.value = buildSnapshot(
                    file = file,
                    collectionStatus = "loaded",
                    lastRefreshEvents = 0,
                    lastRefreshForkExecEvents = 0
                )
                true
            }.getOrElse { error ->
                Logger.e(LOG_TAG, "manual telemetry archive/truncate failed: ${error.message}")
                false
            }
        }
        if (!rotated) {
            return ProotTelemetryRepairExecutionResult(
                status = ProotTelemetryRepairExecutionStatus.ROTATE_FAILED,
                action = plan.action,
                reason = "archive_or_truncate_failed:$reason",
                sourcePath = file.absolutePath,
                archivePath = archiveFile.absolutePath,
                previousTotalEvents = before.counters.totalEvents,
                previousSkippedBytes = before.counters.skippedBytes,
                previousParseErrors = before.counters.parseErrors
            )
        }

        Logger.i(LOG_TAG, "manual telemetry archive created and source truncated: ${archiveFile.absolutePath}")
        refresh(appContext)
        return ProotTelemetryRepairExecutionResult(
            status = ProotTelemetryRepairExecutionStatus.ROTATED,
            action = plan.action,
            reason = "manual_archive_then_truncate_history_contaminated_jsonl:$reason",
            sourcePath = file.absolutePath,
            archivePath = archiveFile.absolutePath,
            rotatedBytes = rotatedBytes,
            previousTotalEvents = before.counters.totalEvents,
            previousSkippedBytes = before.counters.skippedBytes,
            previousParseErrors = before.counters.parseErrors
        )
    }

    fun prepareLiveTraceeProbeBaseline(
        context: Context,
        targetLiveTracees: Int,
        reason: String = "manual-probe-prepare"
    ): ProotTelemetryProbePrepareExecutionResult {
        val appContext = context.applicationContext
        runCatching { readTelemetry(appContext) }
            .onFailure { error ->
                Logger.e(LOG_TAG, "probe prepare refresh failed: ${error.message}")
            }
        val file = telemetryFile(appContext)
        val before = snapshot.value
        val prepared = synchronized(lock) {
            runCatching {
                file.parentFile?.let { parent ->
                    if (!parent.exists()) parent.mkdirs()
                }
                if (!file.exists()) {
                    file.createNewFile()
                }
                // Test-only: keep the native writer fd valid, but reset the Android observer baseline.
                RandomAccessFile(file, "rw").use { raf ->
                    raf.setLength(0L)
                }
                resetReaderState(file.absolutePath)
                probeDeclaredTargetLiveTracees = targetLiveTracees.coerceAtLeast(0)
                _snapshot.value = buildSnapshot(
                    file = file,
                    collectionStatus = "loaded",
                    lastRefreshEvents = 0,
                    lastRefreshForkExecEvents = 0
                )
                true
            }.getOrElse { error ->
                Logger.e(LOG_TAG, "probe prepare baseline reset failed: ${error.message}")
                false
            }
        }
        val status = if (prepared) "PREPARED" else "FAILED"
        val action = "PREPARE_LIVE_TRACEE_PROBE_BASELINE"
        val effectiveReason = if (prepared) {
            "android_control_plane_declared_probe_target_${targetLiveTracees}:$reason"
        } else {
            "android_control_plane_probe_prepare_failed:$reason"
        }
        Logger.i(LOG_TAG, "probe prepare baseline: status=$status target=$targetLiveTracees")
        return ProotTelemetryProbePrepareExecutionResult(
            status = status,
            action = action,
            reason = effectiveReason,
            targetLiveTracees = targetLiveTracees.coerceAtLeast(0),
            sourcePath = file.absolutePath,
            previousLiveTracees = before.liveTraceeCount,
            previousPressureScore = before.pressureWindow.pressureScore,
            previousTotalEvents = before.counters.totalEvents
        )
    }

    fun injectLiveTraceeProbeSample(
        context: Context,
        targetLiveTracees: Int,
        reason: String = "manual-probe-inject"
    ): ProotTelemetryProbeInjectExecutionResult {
        val appContext = context.applicationContext
        runCatching { readTelemetry(appContext) }
            .onFailure { error ->
                Logger.e(LOG_TAG, "probe inject preflight refresh failed: ${error.message}")
            }

        val file = telemetryFile(appContext)
        val before = snapshot.value
        val target = targetLiveTracees.coerceAtLeast(0)
        val baselineClean = before.liveTraceeCount == 0 &&
            before.pressureWindow.pressureScore == 0 &&
            before.pressureWindow.eventsInWindow == 0 &&
            before.pressureWindow.forkExecEventsInWindow == 0
        val targetMatchesDeclaration = before.probeDeclaredTargetLiveTracees == target

        if (target <= 0 || !baselineClean || !targetMatchesDeclaration) {
            return ProotTelemetryProbeInjectExecutionResult(
                status = "NOT_READY",
                action = "INJECT_LIVE_TRACEE_PROBE_SAMPLE",
                reason = "probe_inject_not_ready:target=$target,declared=${before.probeDeclaredTargetLiveTracees}," +
                    "baselineClean=$baselineClean:$reason",
                targetLiveTracees = target,
                sourcePath = file.absolutePath,
                previousLiveTracees = before.liveTraceeCount,
                previousPressureScore = before.pressureWindow.pressureScore,
                previousEventsInWindow = before.pressureWindow.eventsInWindow,
                previousForkExecEventsInWindow = before.pressureWindow.forkExecEventsInWindow
            )
        }

        val eventTimestamp = System.currentTimeMillis() - SYNTHETIC_PROBE_EVENT_AGE_MS
        val syntheticProotPid = 900_000 + target
        val injected = synchronized(lock) {
            runCatching {
                file.parentFile?.let { parent ->
                    if (!parent.exists()) parent.mkdirs()
                }
                if (!file.exists()) {
                    file.createNewFile()
                }
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(file.length())
                    repeat(target) { index ->
                        val traceePid = 910_000 + target * 1_000 + index
                        val line = JSONObject()
                            .put("eventType", ProotTelemetryEventType.TraceeCreated.name)
                            .put("timestampMs", eventTimestamp)
                            .put("prootPid", syntheticProotPid)
                            .put("traceePid", traceePid)
                            .put("traceeVpid", index + 1L)
                            .put("flags", 0L)
                            .put("sourceHook", "android_controlled_live_tracee_probe")
                            .put("costLevel", "probe_live_tracee_n$target")
                            .toString()
                        raf.write(line.toByteArray(Charsets.UTF_8))
                        raf.write('\n'.code)
                    }
                }
                true
            }.getOrElse { error ->
                Logger.e(LOG_TAG, "probe inject sample write failed: ${error.message}")
                false
            }
        }

        if (!injected) {
            return ProotTelemetryProbeInjectExecutionResult(
                status = "FAILED",
                action = "INJECT_LIVE_TRACEE_PROBE_SAMPLE",
                reason = "probe_inject_write_failed:$reason",
                targetLiveTracees = target,
                sourcePath = file.absolutePath,
                previousLiveTracees = before.liveTraceeCount,
                previousPressureScore = before.pressureWindow.pressureScore,
                previousEventsInWindow = before.pressureWindow.eventsInWindow,
                previousForkExecEventsInWindow = before.pressureWindow.forkExecEventsInWindow
            )
        }

        readTelemetry(appContext)
        val after = snapshot.value
        Logger.i(
            LOG_TAG,
            "probe inject sample: target=$target live=${after.liveTraceeCount} " +
                "score=${after.pressureWindow.pressureScore} events=${after.pressureWindow.eventsInWindow} " +
                "forkExec=${after.pressureWindow.forkExecEventsInWindow}"
        )
        return ProotTelemetryProbeInjectExecutionResult(
            status = "INJECTED",
            action = "INJECT_LIVE_TRACEE_PROBE_SAMPLE",
            reason = "android_control_plane_injected_tracee_created_sample_old_timestamp:$reason",
            targetLiveTracees = target,
            sourcePath = file.absolutePath,
            previousLiveTracees = before.liveTraceeCount,
            previousPressureScore = before.pressureWindow.pressureScore,
            previousEventsInWindow = before.pressureWindow.eventsInWindow,
            previousForkExecEventsInWindow = before.pressureWindow.forkExecEventsInWindow,
            observedLiveTracees = after.liveTraceeCount,
            observedPressureScore = after.pressureWindow.pressureScore,
            observedEventsInWindow = after.pressureWindow.eventsInWindow,
            observedForkExecEventsInWindow = after.pressureWindow.forkExecEventsInWindow
        )
    }

    fun appendTelemetryHeartbeat(
        context: Context,
        reason: String = "manual-telemetry-heartbeat"
    ): ProotTelemetryHeartbeatExecutionResult {
        val appContext = context.applicationContext
        runCatching { readTelemetry(appContext) }
            .onFailure { error ->
                Logger.e(LOG_TAG, "telemetry heartbeat preflight refresh failed: ${error.message}")
            }

        val file = telemetryFile(appContext)
        val before = snapshot.value
        val now = System.currentTimeMillis()
        val appended = synchronized(lock) {
            runCatching {
                file.parentFile?.let { parent ->
                    if (!parent.exists()) parent.mkdirs()
                }
                if (!file.exists()) {
                    file.createNewFile()
                }
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(file.length())
                    val line = JSONObject()
                        .put("eventType", ProotTelemetryEventType.ProotTelemetryStarted.name)
                        .put("timestampMs", now)
                        .put("prootPid", 0)
                        .put("traceePid", 0)
                        .put("traceeVpid", 0L)
                        .put("flags", 0L)
                        .put("sourceHook", "android_control_plane_telemetry_heartbeat")
                        .put("costLevel", "heartbeat_no_tracee")
                        .toString()
                    raf.write(line.toByteArray(Charsets.UTF_8))
                    raf.write('\n'.code)
                }
                true
            }.getOrElse { error ->
                Logger.e(LOG_TAG, "telemetry heartbeat append failed: ${error.message}")
                false
            }
        }

        if (!appended) {
            return ProotTelemetryHeartbeatExecutionResult(
                status = "FAILED",
                action = "REFRESH_PROOT_TELEMETRY_HEARTBEAT",
                reason = "heartbeat_append_failed:$reason",
                sourcePath = file.absolutePath,
                previousLiveTracees = before.liveTraceeCount,
                previousPressureScore = before.pressureWindow.pressureScore,
                previousEventsInWindow = before.pressureWindow.eventsInWindow,
                previousForkExecEventsInWindow = before.pressureWindow.forkExecEventsInWindow
            )
        }

        readTelemetry(appContext)
        val after = snapshot.value
        Logger.i(
            LOG_TAG,
            "telemetry heartbeat appended: live=${after.liveTraceeCount} " +
                "score=${after.pressureWindow.pressureScore} events=${after.pressureWindow.eventsInWindow} " +
                "forkExec=${after.pressureWindow.forkExecEventsInWindow}"
        )
        return ProotTelemetryHeartbeatExecutionResult(
            status = "APPENDED",
            action = "REFRESH_PROOT_TELEMETRY_HEARTBEAT",
            reason = "android_control_plane_heartbeat_no_tracee_no_probe:$reason",
            sourcePath = file.absolutePath,
            previousLiveTracees = before.liveTraceeCount,
            previousPressureScore = before.pressureWindow.pressureScore,
            previousEventsInWindow = before.pressureWindow.eventsInWindow,
            previousForkExecEventsInWindow = before.pressureWindow.forkExecEventsInWindow,
            observedLiveTracees = after.liveTraceeCount,
            observedPressureScore = after.pressureWindow.pressureScore,
            observedEventsInWindow = after.pressureWindow.eventsInWindow,
            observedForkExecEventsInWindow = after.pressureWindow.forkExecEventsInWindow
        )
    }

    fun retireOwnerTracees(
        context: Context,
        ownerId: String,
        reason: String = "owner-stop-confirmed"
    ): ProotTelemetryOwnerRetireResult {
        val cleanOwnerId = ownerId.trim()
        val appContext = context.applicationContext
        val file = telemetryFile(appContext)
        if (cleanOwnerId.isBlank()) {
            return ProotTelemetryOwnerRetireResult(
                status = "SKIPPED",
                action = "RETIRE_PROOT_OWNER_TRACEES",
                ownerId = "",
                reason = "owner_id_missing:$reason",
                sourcePath = file.absolutePath
            )
        }

        runCatching { readTelemetry(appContext) }
            .onFailure { error ->
                Logger.e(LOG_TAG, "owner retire preflight refresh failed: ${error.message}")
            }

        val before = snapshot.value
        val targets = before.tracees
            .filter { it.running && it.kfRuntimeId == cleanOwnerId }
            .sortedBy { it.traceePid }
        if (targets.isEmpty()) {
            return ProotTelemetryOwnerRetireResult(
                status = "NOOP",
                action = "RETIRE_PROOT_OWNER_TRACEES",
                ownerId = cleanOwnerId,
                reason = "owner_has_no_running_tracees:$reason",
                sourcePath = file.absolutePath,
                previousLiveTracees = before.liveTraceeCount,
                observedLiveTracees = before.liveTraceeCount
            )
        }

        val safeReason = telemetryLabel(reason, "owner_stop_confirmed")
        val now = System.currentTimeMillis()
        val appended = synchronized(lock) {
            runCatching {
                file.parentFile?.let { parent ->
                    if (!parent.exists()) parent.mkdirs()
                }
                if (!file.exists()) {
                    file.createNewFile()
                }
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(file.length())
                    targets.forEachIndexed { index, record ->
                        val line = JSONObject()
                            .put("schema", "kf_proot_lifecycle_event_v1")
                            .put("eventType", ProotTelemetryEventType.TraceeExited.name)
                            .put("timestampMs", now + index)
                            .put("telemetrySessionId", record.telemetrySessionId)
                            .put("prootStartMs", record.prootStartMs)
                            .put("prootPid", record.prootPid)
                            .put("traceePid", record.traceePid)
                            .put("traceeVpid", record.traceeVpid)
                            .put("processGroupId", record.processGroupId ?: 0)
                            .put("sessionId", record.sessionId ?: 0)
                            .put("parentTraceePid", record.parentTraceePid ?: 0)
                            .put("parentTraceeVpid", record.parentTraceeVpid ?: 0L)
                            .put("state", "EXITED")
                            .put("flags", 0L)
                            .put("sourceHook", "kite_owner_retire_$safeReason")
                            .put("costLevel", "control_plane_owner_retire")
                            .put("executable", record.executable)
                            .put("argvHash", record.argvHash)
                            .put("argvPreview", record.argvPreview)
                            .put("cwd", record.cwd)
                            .put("kfRuntimeId", record.kfRuntimeId)
                            .put("kfUnitId", record.kfUnitId)
                            .put("exitCode", -1)
                            .toString()
                        raf.write(line.toByteArray(Charsets.UTF_8))
                        raf.write('\n'.code)
                    }
                }
                true
            }.getOrElse { error ->
                Logger.e(LOG_TAG, "owner retire append failed: ${error.message}")
                false
            }
        }

        if (!appended) {
            return ProotTelemetryOwnerRetireResult(
                status = "FAILED",
                action = "RETIRE_PROOT_OWNER_TRACEES",
                ownerId = cleanOwnerId,
                reason = "owner_retire_append_failed:$reason",
                sourcePath = file.absolutePath,
                retiredTraceePids = targets.map { it.traceePid },
                previousLiveTracees = before.liveTraceeCount,
                observedLiveTracees = before.liveTraceeCount
            )
        }

        readTelemetry(appContext)
        val after = snapshot.value
        Logger.i(
            LOG_TAG,
            "owner tracees retired: owner=$cleanOwnerId pids=${targets.joinToString(",") { it.traceePid.toString() }} " +
                "live=${before.liveTraceeCount}->${after.liveTraceeCount}"
        )
        return ProotTelemetryOwnerRetireResult(
            status = "RETIRED",
            action = "RETIRE_PROOT_OWNER_TRACEES",
            ownerId = cleanOwnerId,
            reason = "android_control_plane_owner_tombstone_same_telemetry_source:$reason",
            sourcePath = file.absolutePath,
            retiredTraceePids = targets.map { it.traceePid },
            previousLiveTracees = before.liveTraceeCount,
            observedLiveTracees = after.liveTraceeCount
        )
    }

    private fun readTelemetry(context: Context) {
        synchronized(readerLock) {
            readTelemetryLocked(context)
        }
    }

    private fun readTelemetryLocked(context: Context) {
        val file = telemetryFile(context)
        val activeRegistry = readActiveRegistryIfNeeded(context)
        if (!file.exists()) {
            synchronized(lock) {
                resetIfPathChanged(file.absolutePath)
                activeRegistry?.let { reconcileActiveRegistry(it) }
                _snapshot.value = buildSnapshot(
                    file = file,
                    collectionStatus = "file_missing",
                    lastRefreshEvents = 0,
                    lastRefreshForkExecEvents = 0
                )
            }
            return
        }

        val baselineResult = runCatching { readRotationBaselineIfNeeded(file) }
            .getOrElse { error ->
                Logger.e(LOG_TAG, "failed to read rotated PRoot telemetry baseline: ${error.message}")
                null
            }
        val readResult = runCatching { readNewLines(file) }
            .getOrElse { error ->
                Logger.e(LOG_TAG, "failed to read PRoot telemetry: ${error.message}")
                synchronized(lock) {
                    _snapshot.value = buildSnapshot(
                        file = file,
                        collectionStatus = "read_error:${error.javaClass.simpleName}",
                        lastRefreshEvents = 0,
                        lastRefreshForkExecEvents = 0
                    )
                }
                return
            }

        var parsedEvents = 0
        var forkExecEvents = 0
        var parseErrors = 0
        val baselineNow = System.currentTimeMillis()

        synchronized(lock) {
            resetIfPathChanged(file.absolutePath)
            // Large JSONL files are tailed from the newest complete line so the reader can catch up
            // without replaying stale history. Dropping old backlog is not corruption; parse errors
            // remain the health blocker for genuinely malformed telemetry.
            counters = counters

            baselineResult?.let { baseline ->
                ownerEvidenceCompleteFromMs = baseline.ownerEvidenceCompleteFromMs
                ownerEvidenceCoverageReason = baseline.ownerEvidenceCoverageReason
                if (baseline.skippedBytes > 0L) {
                    counters = counters.copy(skippedBytes = counters.skippedBytes + baseline.skippedBytes)
                }
                for (line in baseline.lines) {
                    if (line.isBlank()) continue
                    val event = parseEvent(line)
                    if (event == null) {
                        parseErrors += 1
                        continue
                    }
                    if (!event.belongsToRotationBaseline(baselineNow)) {
                        continue
                    }
                    parsedEvents += 1
                    if (event.eventType in FORK_EXEC_EVENT_TYPES) {
                        forkExecEvents += 1
                    }
                    applyEvent(event)
                }
            }

            if (readResult.skippedBytes > 0L) {
                counters = counters.copy(skippedBytes = counters.skippedBytes + readResult.skippedBytes)
                ownerEvidenceCompleteFromMs = maxOf(
                    ownerEvidenceCompleteFromMs,
                    firstEventTimestamp(readResult.lines) ?: baselineNow
                )
                ownerEvidenceCoverageReason = "incremental_tail_gap"
                activeRegistryRecoveryPending = true
            }

            for (line in readResult.lines) {
                if (line.isBlank()) continue
                val event = parseEvent(line)
                if (event == null) {
                    parseErrors += 1
                    continue
                }
                if (!event.belongsToCurrentReaderEpoch()) {
                    continue
                }
                parsedEvents += 1
                if (event.eventType in FORK_EXEC_EVENT_TYPES) {
                    forkExecEvents += 1
                }
                applyEvent(event)
            }
            if (parseErrors > 0) {
                counters = counters.copy(parseErrors = counters.parseErrors + parseErrors)
                ownerEvidenceCompleteFromMs = maxOf(ownerEvidenceCompleteFromMs, baselineNow)
                ownerEvidenceCoverageReason = "telemetry_parse_error"
                activeRegistryRecoveryPending = true
            }

            // 先重放本轮文件事件，再用同一时刻读取的活动注册表收敛。否则冷启动时
            // 注册表会在 tracees 仍为空时被标记为已恢复，随后当前 JSONL 中的旧记录
            // 又会被重放成运行中，并且不再获得第二次注册表校准。
            activeRegistry?.let { reconcileActiveRegistry(it) }

            _snapshot.value = buildSnapshot(
                file = file,
                collectionStatus = "loaded",
                lastRefreshEvents = parsedEvents,
                lastRefreshForkExecEvents = forkExecEvents
            )
        }
    }

    private fun telemetryFile(context: Context): File {
        return File(AssetExtractor.getRuntimeLayout(context).tmpDir, TELEMETRY_FILE_NAME)
    }

    private fun activeRegistryRoot(context: Context): File {
        return File(AssetExtractor.getRuntimeLayout(context).tmpDir, "kf-proot-active")
    }

    private fun readActiveRegistryIfNeeded(context: Context): ProotActiveRegistrySnapshot? {
        val shouldRead = synchronized(lock) { activeRegistryRecoveryPending }
        if (!shouldRead) return null
        return ProotActiveRegistryReader(activeRegistryRoot(context)).read()
    }

    private fun reconcileActiveRegistry(
        registry: ProotActiveRegistrySnapshot,
        now: Long = System.currentTimeMillis(),
    ) {
        latestActiveRegistry = registry
        val registryActiveKeys = mutableSetOf<TraceeLifecycleKey>()
        registry.sessions.forEach { session ->
            val activeKeys = mutableSetOf<TraceeLifecycleKey>()
            session.entries.forEach entryLoop@{ entry ->
                val key = TraceeLifecycleKey(
                    telemetrySessionId = entry.telemetrySessionId,
                    prootStartMs = entry.prootStartMs,
                    prootPid = entry.prootPid,
                    traceePid = entry.traceePid,
                    lifecycleSeq = entry.lifecycleSeq,
                )
                val verification = processVerifier.verify(entry.processRef())
                if (verification.terminal) {
                    tracees[key]?.let { existing ->
                        tracees[key] = existing.copy(
                            lastEventAtMs = now,
                            lastSourceHook = "active_registry_identity_terminal",
                            exitedAtMs = now,
                            eventSeq = maxOf(existing.eventSeq, entry.lastEventSeq),
                            kernelState = verification.kernelState,
                            verificationStatus = verification.status,
                            verifiedAtElapsedMs = verification.verifiedAtElapsedMs,
                            evidenceSource = "active_registry+direct_probe",
                        )
                    }
                    return@entryLoop
                }

                activeKeys += key
                registryActiveKeys += key
                val existing = tracees[key]
                val parentOwner = entry.parentLifecycleSeq?.let { parentSeq ->
                    tracees.entries.firstOrNull { (parentKey, parentRecord) ->
                        parentKey.telemetrySessionId == entry.telemetrySessionId &&
                            parentKey.lifecycleSeq == parentSeq &&
                            parentRecord.running
                    }?.value
                }
                tracees[key] = (existing ?: ProotTraceeRecord(
                    traceePid = entry.traceePid,
                    traceeVpid = entry.traceeVpid,
                    telemetrySessionId = entry.telemetrySessionId,
                    prootStartMs = entry.prootStartMs,
                    prootPid = entry.prootPid,
                    processGroupId = entry.processGroupId,
                    sessionId = entry.sessionId,
                    parentTraceePid = entry.parentTraceePid,
                    parentTraceeVpid = entry.parentTraceeVpid,
                    createdAtMs = now,
                    lastEventAtMs = now,
                    lastEventType = entry.lastEventType,
                    lastSourceHook = "active_registry_reconcile",
                    executable = entry.executable,
                    argvHash = entry.argvHash,
                    argvPreview = entry.argvPreview,
                    cwd = entry.cwd,
                    kfRuntimeId = entry.kfRuntimeId.ifBlank { parentOwner?.kfRuntimeId.orEmpty() },
                    kfUnitId = entry.kfUnitId.ifBlank { parentOwner?.kfUnitId.orEmpty() },
                    eventSeq = entry.lastEventSeq,
                    lifecycleSeq = entry.lifecycleSeq,
                    startTimeTicks = entry.startTimeTicks,
                    parentLifecycleSeq = entry.parentLifecycleSeq,
                )).copy(
                    processGroupId = verification.processGroupId ?: entry.processGroupId,
                    sessionId = verification.sessionId ?: entry.sessionId,
                    parentTraceePid = verification.parentPid ?: entry.parentTraceePid,
                    parentTraceeVpid = entry.parentTraceeVpid,
                    lastEventAtMs = now,
                    lastEventType = entry.lastEventType,
                    lastSourceHook = "active_registry_reconcile",
                    executable = entry.executable.ifBlank { existing?.executable.orEmpty() },
                    argvHash = entry.argvHash.ifBlank { existing?.argvHash.orEmpty() },
                    argvPreview = entry.argvPreview.ifBlank { existing?.argvPreview.orEmpty() },
                    cwd = entry.cwd.ifBlank { existing?.cwd.orEmpty() },
                    kfRuntimeId = entry.kfRuntimeId.ifBlank {
                        existing?.kfRuntimeId ?: parentOwner?.kfRuntimeId.orEmpty()
                    },
                    kfUnitId = entry.kfUnitId.ifBlank {
                        existing?.kfUnitId ?: parentOwner?.kfUnitId.orEmpty()
                    },
                    exitedAtMs = null,
                    signaledAtMs = null,
                    exitCode = null,
                    signal = null,
                    eventSeq = maxOf(existing?.eventSeq ?: 0L, entry.lastEventSeq),
                    startTimeTicks = entry.startTimeTicks.takeIf { it > 0L }
                        ?: existing?.startTimeTicks
                        ?: 0L,
                    parentLifecycleSeq = entry.parentLifecycleSeq ?: existing?.parentLifecycleSeq,
                    kernelState = verification.kernelState,
                    verificationStatus = verification.status,
                    verifiedAtElapsedMs = verification.verifiedAtElapsedMs,
                    evidenceSource = "active_registry+direct_probe",
                )
            }

            tracees.entries
                .filter { (key, record) ->
                    key.telemetrySessionId == session.telemetrySessionId &&
                        record.running &&
                        key !in activeKeys &&
                        now - maxOf(record.createdAtMs, record.lastEventAtMs) >=
                            ACTIVE_REGISTRY_SESSION_CREATION_GRACE_MS
                }
                .map { it.key }
                .forEach { key ->
                    val record = tracees.getValue(key)
                    tracees[key] = record.copy(
                        lastEventAtMs = now,
                        lastSourceHook = "active_registry_snapshot_missing",
                        exitedAtMs = now,
                        evidenceSource = "active_registry",
                    )
                }
            eventContinuity.reset(session.telemetrySessionId, session.lastEventSeq)
        }

        val activeSessionIds = registry.sessions
            .mapTo(mutableSetOf(), ProotActiveRegistrySession::telemetrySessionId)
        val observedSessionIds = activeSessionIds.apply {
            addAll(registry.unstableSessionIds)
        }
        if (registry.status != ProotActiveRegistryReadStatus.MISSING) {
            tracees.entries
                .filter { (_, record) ->
                    record.running &&
                        record.telemetrySessionId.isNotBlank() &&
                        record.telemetrySessionId !in observedSessionIds &&
                        now - maxOf(record.createdAtMs, record.lastEventAtMs) >=
                            ACTIVE_REGISTRY_SESSION_CREATION_GRACE_MS
                }
                .map { it.key }
                .forEach { key ->
                    val record = tracees.getValue(key)
                    tracees[key] = record.copy(
                        lastEventAtMs = now,
                        lastSourceHook = "active_registry_session_missing",
                        exitedAtMs = now,
                        evidenceSource = "active_registry",
                    )
                }
        }

        val unresolvedRegistryRecord = tracees.entries.any { (key, record) ->
            record.running &&
                record.telemetrySessionId.isNotBlank() &&
                (
                    record.telemetrySessionId !in observedSessionIds ||
                        key !in registryActiveKeys
                    )
        }
        val completeForObservedSessions =
            registry.status != ProotActiveRegistryReadStatus.MISSING && !unresolvedRegistryRecord
        if (completeForObservedSessions) {
            ownerEvidenceCompleteFromMs = 0L
            ownerEvidenceCoverageReason = "active_registry_reconciled"
            activeRegistryRecoveryPending = false
            activeRegistryReconciledAtMs = now
        } else {
            activeRegistryRecoveryPending = true
        }
    }

    private fun telemetryLabel(value: String, fallback: String): String {
        return value
            .trim()
            .take(80)
            .replace(Regex("[^A-Za-z0-9_.:-]+"), "_")
            .ifBlank { fallback }
    }

    private fun readRotationBaselineIfNeeded(file: File): BaselineReadResult? {
        val path = file.absolutePath
        val shouldLoad = synchronized(lock) {
            resetIfPathChanged(path)
            rotationBaselineLoadedForPath != path &&
                counters.totalEvents == 0L &&
                recentEvents.isEmpty() &&
                tracees.isEmpty()
        }
        if (!shouldLoad) return null

        var skippedBytes = 0L
        val lines = mutableListOf<String>()
        val rotatedFiles = rotatedTelemetryBaselineFiles(file)
        for (segment in rotatedFiles) {
            val result = readCompleteTailLines(segment, ROTATED_BASELINE_READ_BYTES)
            skippedBytes += result.skippedBytes
            lines += result.lines
        }
        val currentResult = readCompleteTailLines(file, ROTATED_BASELINE_READ_BYTES)
        skippedBytes += currentResult.skippedBytes
        lines += currentResult.lines
        val currentLength = file.length()
        val fullHistoryLoaded = rotatedFiles.isEmpty() && currentResult.skippedBytes == 0L
        val baselineCoverageStart = if (fullHistoryLoaded) {
            0L
        } else {
            firstEventTimestamp(currentResult.lines) ?: synchronized(lock) { readerEpochMs }
        }
        val baselineCoverageReason = when {
            fullHistoryLoaded -> "full_history"
            currentResult.skippedBytes > 0L -> "historical_tail_skipped"
            else -> "rotated_history_boundary"
        }

        val loaded = synchronized(lock) {
            resetIfPathChanged(path)
            if (
                rotationBaselineLoadedForPath == path ||
                counters.totalEvents != 0L ||
                recentEvents.isNotEmpty() ||
                tracees.isNotEmpty()
            ) {
                false
            } else {
                rotationBaselineLoadedForPath = path
                lastOffsetBytes = currentLength
                pendingPartialLine = ""
                true
            }
        }
        return if (loaded) {
            BaselineReadResult(
                lines = lines,
                skippedBytes = skippedBytes,
                ownerEvidenceCompleteFromMs = baselineCoverageStart,
                ownerEvidenceCoverageReason = baselineCoverageReason
            )
        } else {
            null
        }
    }

    private fun rotatedTelemetryBaselineFiles(file: File): List<File> {
        val parent = file.parentFile ?: return emptyList()
        return (ROTATED_TELEMETRY_SEGMENTS downTo 1)
            .map { index -> File(parent, "${file.name}.$index") }
            .filter { it.exists() && it.isFile }
    }

    private fun firstEventTimestamp(lines: List<String>): Long? = lines
        .asSequence()
        .filter(String::isNotBlank)
        .mapNotNull(::parseEvent)
        .firstOrNull()
        ?.timestampMs

    private fun readCompleteTailLines(file: File, maxBytes: Int): ReadLinesResult {
        if (!file.exists() || !file.isFile) return ReadLinesResult(emptyList(), 0L)
        val length = file.length()
        if (length <= 0L) return ReadLinesResult(emptyList(), 0L)

        val startOffset = (length - maxBytes).coerceAtLeast(0L)
        val bytes = ByteArray((length - startOffset).coerceAtMost(maxBytes.toLong()).toInt())
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(startOffset)
            raf.readFully(bytes)
        }

        val rawText = bytes.toString(Charsets.UTF_8)
        val preparedText = if (startOffset > 0L) {
            rawText.substringAfter('\n', "")
        } else {
            rawText
        }
        if (preparedText.isEmpty()) return ReadLinesResult(emptyList(), startOffset)
        val parts = preparedText.split('\n')
        val completeLines = if (preparedText.endsWith('\n')) {
            parts.dropLast(1)
        } else {
            parts.dropLast(1)
        }
        return ReadLinesResult(completeLines, startOffset)
    }

    private fun readNewLines(file: File): ReadLinesResult {
        synchronized(lock) {
            resetIfPathChanged(file.absolutePath)
        }
        val length = file.length()
        var offset = synchronized(lock) { lastOffsetBytes }
        if (length < offset) {
            synchronized(lock) {
                resetReaderState(file.absolutePath)
                offset = lastOffsetBytes
            }
        }
        val initialStaleAttach = synchronized(lock) {
            offset == 0L &&
                counters.totalEvents == 0L &&
                recentEvents.isEmpty() &&
                tracees.isEmpty() &&
                file.lastModified() in 1 until readerEpochMs
        }
        val startOffset = when {
            initialStaleAttach -> length
            length - offset > MAX_INCREMENTAL_READ_BYTES -> (length - MAX_INCREMENTAL_READ_BYTES).coerceAtLeast(0L)
            else -> offset
        }
        val skippedBytes = if (startOffset > offset) startOffset - offset else 0L
        if (length <= startOffset) {
            synchronized(lock) {
                lastOffsetBytes = length
            }
            return ReadLinesResult(emptyList(), skippedBytes)
        }

        val bytes = ByteArray((length - startOffset).coerceAtMost(MAX_INCREMENTAL_READ_BYTES.toLong()).toInt())
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(startOffset)
            raf.readFully(bytes)
        }

        val rawText = bytes.toString(Charsets.UTF_8)
        val preparedText = synchronized(lock) {
            if (skippedBytes > 0L) {
                pendingPartialLine = ""
                rawText.substringAfter('\n', "")
            } else {
                pendingPartialLine + rawText
            }
        }
        val complete = preparedText.endsWith('\n')
        val parts = preparedText.split('\n')
        val completeLines = if (complete) {
            parts.dropLast(1)
        } else {
            parts.dropLast(1)
        }
        synchronized(lock) {
            pendingPartialLine = if (complete) "" else parts.lastOrNull().orEmpty()
            lastOffsetBytes = length
        }
        return ReadLinesResult(completeLines, skippedBytes)
    }

    private fun parseEvent(line: String): ProotTelemetryEvent? {
        return runCatching {
            val json = JSONObject(line)
            val type = ProotTelemetryEventType.entries.firstOrNull {
                it.name == json.optString("eventType")
            } ?: ProotTelemetryEventType.Unknown
            ProotTelemetryEvent(
                eventType = type,
                timestampMs = json.optLong("timestampMs", System.currentTimeMillis()),
                schema = json.optString("schema", ""),
                telemetrySessionId = json.optString("telemetrySessionId", ""),
                prootStartMs = json.optLong("prootStartMs", 0L),
                prootPid = json.optInt("prootPid", 0),
                traceePid = json.optInt("traceePid", 0),
                traceeVpid = json.optLong("traceeVpid", 0L),
                eventSeq = json.optLong("eventSeq", 0L),
                lifecycleSeq = json.optLong("lifecycleSeq", 0L),
                startTimeTicks = json.optLong("startTimeTicks", 0L),
                processGroupId = json.optIntOrNull("processGroupId")?.takeIf { it > 0 },
                sessionId = json.optIntOrNull("sessionId")?.takeIf { it > 0 },
                parentTraceePid = json.optIntOrNull("parentTraceePid")?.takeIf { it > 0 },
                parentTraceeVpid = json.optLongOrNull("parentTraceeVpid")?.takeIf { it > 0L },
                parentLifecycleSeq = json.optLongOrNull("parentLifecycleSeq")?.takeIf { it > 0L },
                flags = json.optLong("flags", 0L),
                sourceHook = json.optString("sourceHook", "unknown"),
                costLevel = json.optString("costLevel", "lifecycle_low"),
                executable = json.optString("executable", ""),
                argvHash = json.optString("argvHash", ""),
                argvPreview = json.optString("argvPreview", ""),
                cwd = json.optString("cwd", ""),
                kfRuntimeId = json.optString("kfRuntimeId", ""),
                kfUnitId = json.optString("kfUnitId", ""),
                exitCode = json.optIntOrNull("exitCode"),
                signal = json.optIntOrNull("signal")
            )
        }.getOrNull()
    }

    private fun applyEvent(event: ProotTelemetryEvent) {
        val continuity = eventContinuity.observe(event.telemetrySessionId, event.eventSeq)
        if (continuity.status == ProotEventContinuityStatus.DUPLICATE_OR_OLD) {
            counters = counters.copy(duplicateEvents = counters.duplicateEvents + 1L)
            return
        }
        if (continuity.requiresSnapshot) {
            counters = counters.copy(sequenceGaps = counters.sequenceGaps + 1L)
            ownerEvidenceCompleteFromMs = maxOf(ownerEvidenceCompleteFromMs, event.timestampMs)
            ownerEvidenceCoverageReason = "event_sequence_gap"
            activeRegistryRecoveryPending = true
        }
        counters = counters.increment(event.eventType)
        pushRecentEvent(event)
        val traceeKey = resolveTraceeLifecycleKey(event)
        val existing = tracees[traceeKey]
        when (event.eventType) {
            ProotTelemetryEventType.ProotTelemetryStarted -> {
                // Startup only proves that native PRoot can open and append telemetry.
            }
            ProotTelemetryEventType.TraceeCreated,
            ProotTelemetryEventType.ForkDetected,
            ProotTelemetryEventType.CloneDetected,
            ProotTelemetryEventType.VforkDetected -> {
                val parentKey = event.parentTraceePid?.let { parentPid ->
                    resolveParentLifecycleKey(event, parentPid)
                }
                val parent = parentKey?.let(tracees::get)
                tracees[traceeKey] = (existing ?: ProotTraceeRecord(
                    traceePid = event.traceePid,
                    traceeVpid = event.traceeVpid,
                    telemetrySessionId = event.telemetrySessionId,
                    prootStartMs = event.prootStartMs,
                    prootPid = event.prootPid,
                    processGroupId = event.processGroupId,
                    sessionId = event.sessionId,
                    parentTraceePid = event.parentTraceePid,
                    parentTraceeVpid = event.parentTraceeVpid,
                    createdAtMs = event.timestampMs,
                    lastEventAtMs = event.timestampMs,
                    lastEventType = event.eventType,
                    lastSourceHook = event.sourceHook,
                    lastCostLevel = event.costLevel,
                    executable = event.executable,
                    argvHash = event.argvHash,
                    argvPreview = event.argvPreview,
                    cwd = event.cwd,
                    kfRuntimeId = event.kfRuntimeId.ifBlank { parent?.kfRuntimeId.orEmpty() },
                    kfUnitId = event.kfUnitId.ifBlank { parent?.kfUnitId.orEmpty() },
                    eventSeq = event.eventSeq,
                    lifecycleSeq = event.lifecycleSeq,
                    startTimeTicks = event.startTimeTicks,
                    parentLifecycleSeq = event.parentLifecycleSeq,
                )).copy(
                    telemetrySessionId = event.telemetrySessionId.ifBlank { existing?.telemetrySessionId.orEmpty() },
                    prootStartMs = event.prootStartMs.takeIf { it > 0L } ?: existing?.prootStartMs ?: 0L,
                    processGroupId = event.processGroupId ?: existing?.processGroupId,
                    sessionId = event.sessionId ?: existing?.sessionId,
                    lastEventAtMs = event.timestampMs,
                    lastEventType = event.eventType,
                    lastSourceHook = event.sourceHook,
                    lastCostLevel = event.costLevel,
                    kfRuntimeId = event.kfRuntimeId.ifBlank { existing?.kfRuntimeId ?: parent?.kfRuntimeId.orEmpty() },
                    kfUnitId = event.kfUnitId.ifBlank { existing?.kfUnitId ?: parent?.kfUnitId.orEmpty() },
                    eventSeq = maxOf(event.eventSeq, existing?.eventSeq ?: 0L),
                    startTimeTicks = event.startTimeTicks.takeIf { it > 0L }
                        ?: existing?.startTimeTicks
                        ?: 0L,
                    parentLifecycleSeq = event.parentLifecycleSeq ?: existing?.parentLifecycleSeq,
                )
                parentKey?.let { key ->
                    tracees[key]?.let { parentRecord ->
                        tracees[key] = parentRecord.copy(
                            childEventCount = parentRecord.childEventCount + 1,
                            lastEventAtMs = event.timestampMs,
                            lastSourceHook = event.sourceHook,
                            lastCostLevel = event.costLevel
                        )
                    }
                }
            }
            ProotTelemetryEventType.ExecDetected -> {
                tracees[traceeKey] = (existing ?: ProotTraceeRecord(
                    traceePid = event.traceePid,
                    traceeVpid = event.traceeVpid,
                    telemetrySessionId = event.telemetrySessionId,
                    prootStartMs = event.prootStartMs,
                    prootPid = event.prootPid,
                    processGroupId = event.processGroupId,
                    sessionId = event.sessionId,
                    parentTraceePid = event.parentTraceePid,
                    parentTraceeVpid = event.parentTraceeVpid,
                    createdAtMs = event.timestampMs,
                    lastEventAtMs = event.timestampMs,
                    lastEventType = event.eventType,
                    lastSourceHook = event.sourceHook,
                    lastCostLevel = event.costLevel,
                    executable = event.executable.ifBlank { existing?.executable.orEmpty() },
                    argvHash = event.argvHash.ifBlank { existing?.argvHash.orEmpty() },
                    argvPreview = event.argvPreview.ifBlank { existing?.argvPreview.orEmpty() },
                    cwd = event.cwd.ifBlank { existing?.cwd.orEmpty() },
                    kfRuntimeId = event.kfRuntimeId.ifBlank { existing?.kfRuntimeId.orEmpty() },
                    kfUnitId = event.kfUnitId.ifBlank { existing?.kfUnitId.orEmpty() },
                    eventSeq = event.eventSeq,
                    lifecycleSeq = event.lifecycleSeq,
                    startTimeTicks = event.startTimeTicks,
                    parentLifecycleSeq = event.parentLifecycleSeq,
                )).copy(
                    telemetrySessionId = event.telemetrySessionId.ifBlank { existing?.telemetrySessionId.orEmpty() },
                    prootStartMs = event.prootStartMs.takeIf { it > 0L } ?: existing?.prootStartMs ?: 0L,
                    processGroupId = event.processGroupId ?: existing?.processGroupId,
                    sessionId = event.sessionId ?: existing?.sessionId,
                    lastEventAtMs = event.timestampMs,
                    lastEventType = event.eventType,
                    lastSourceHook = event.sourceHook,
                    lastCostLevel = event.costLevel,
                    executable = event.executable.ifBlank { existing?.executable.orEmpty() },
                    argvHash = event.argvHash.ifBlank { existing?.argvHash.orEmpty() },
                    argvPreview = event.argvPreview.ifBlank { existing?.argvPreview.orEmpty() },
                    cwd = event.cwd.ifBlank { existing?.cwd.orEmpty() },
                    kfRuntimeId = event.kfRuntimeId.ifBlank { existing?.kfRuntimeId.orEmpty() },
                    kfUnitId = event.kfUnitId.ifBlank { existing?.kfUnitId.orEmpty() },
                    execCount = (existing?.execCount ?: 0) + 1,
                    eventSeq = maxOf(event.eventSeq, existing?.eventSeq ?: 0L),
                    startTimeTicks = event.startTimeTicks.takeIf { it > 0L }
                        ?: existing?.startTimeTicks
                        ?: 0L,
                    parentLifecycleSeq = event.parentLifecycleSeq ?: existing?.parentLifecycleSeq,
                )
            }
            ProotTelemetryEventType.TraceeExited -> {
                tracees[traceeKey] = (existing ?: minimalRecord(event)).copy(
                    telemetrySessionId = event.telemetrySessionId.ifBlank { existing?.telemetrySessionId.orEmpty() },
                    prootStartMs = event.prootStartMs.takeIf { it > 0L } ?: existing?.prootStartMs ?: 0L,
                    processGroupId = event.processGroupId ?: existing?.processGroupId,
                    sessionId = event.sessionId ?: existing?.sessionId,
                    lastEventAtMs = event.timestampMs,
                    lastEventType = event.eventType,
                    lastSourceHook = event.sourceHook,
                    lastCostLevel = event.costLevel,
                    exitedAtMs = event.timestampMs,
                    exitCode = event.exitCode,
                    eventSeq = maxOf(event.eventSeq, existing?.eventSeq ?: 0L),
                    startTimeTicks = event.startTimeTicks.takeIf { it > 0L }
                        ?: existing?.startTimeTicks
                        ?: 0L,
                )
            }
            ProotTelemetryEventType.TraceeSignaled -> {
                tracees[traceeKey] = (existing ?: minimalRecord(event)).copy(
                    telemetrySessionId = event.telemetrySessionId.ifBlank { existing?.telemetrySessionId.orEmpty() },
                    prootStartMs = event.prootStartMs.takeIf { it > 0L } ?: existing?.prootStartMs ?: 0L,
                    processGroupId = event.processGroupId ?: existing?.processGroupId,
                    sessionId = event.sessionId ?: existing?.sessionId,
                    lastEventAtMs = event.timestampMs,
                    lastEventType = event.eventType,
                    lastSourceHook = event.sourceHook,
                    lastCostLevel = event.costLevel,
                    signaledAtMs = event.timestampMs,
                    signal = event.signal,
                    eventSeq = maxOf(event.eventSeq, existing?.eventSeq ?: 0L),
                    startTimeTicks = event.startTimeTicks.takeIf { it > 0L }
                        ?: existing?.startTimeTicks
                        ?: 0L,
                )
            }
            ProotTelemetryEventType.Unknown -> {
                if (existing != null) {
                    tracees[traceeKey] = existing.copy(
                        lastEventAtMs = event.timestampMs,
                        lastEventType = event.eventType,
                        lastSourceHook = event.sourceHook,
                        lastCostLevel = event.costLevel
                    )
                }
            }
        }
        trimTracees()
    }

    private fun resolveTraceeLifecycleKey(event: ProotTelemetryEvent): TraceeLifecycleKey {
        val exact = event.exactLifecycleKey()
        if (event.hasExplicitLifecycleIdentity()) return exact

        val candidates = tracees.entries
            .asSequence()
            .filter { (key, _) ->
                key.prootPid == event.prootPid && key.traceePid == event.traceePid
            }
            .sortedByDescending { (_, record) -> record.lastEventAtMs }
            .toList()
        val running = candidates.firstOrNull { (_, record) -> record.running }
        if (event.eventType in TRACEE_LIFECYCLE_START_EVENTS && running == null) {
            return exact.copy(legacyGenerationMs = event.timestampMs)
        }
        return running?.key
            ?: candidates.firstOrNull()?.key
            ?: exact.copy(legacyGenerationMs = event.timestampMs)
    }

    private fun resolveParentLifecycleKey(
        event: ProotTelemetryEvent,
        parentTraceePid: Int
    ): TraceeLifecycleKey? {
        val exact = TraceeLifecycleKey(
            telemetrySessionId = event.telemetrySessionId,
            prootStartMs = event.prootStartMs,
            prootPid = event.prootPid,
            traceePid = parentTraceePid,
            lifecycleSeq = event.parentLifecycleSeq ?: 0L,
        )
        if (event.hasExplicitLifecycleIdentity() && event.parentLifecycleSeq != null) {
            return exact.takeIf(tracees::containsKey)
        }
        return tracees.entries
            .asSequence()
            .filter { (key, record) ->
                key.prootPid == event.prootPid &&
                    (event.telemetrySessionId.isBlank() || key.telemetrySessionId == event.telemetrySessionId) &&
                    key.traceePid == parentTraceePid &&
                    record.running
            }
            .maxByOrNull { (_, record) -> record.lastEventAtMs }
            ?.key
    }

    private fun minimalRecord(event: ProotTelemetryEvent): ProotTraceeRecord {
        return ProotTraceeRecord(
            traceePid = event.traceePid,
            traceeVpid = event.traceeVpid,
            telemetrySessionId = event.telemetrySessionId,
            prootStartMs = event.prootStartMs,
            prootPid = event.prootPid,
            processGroupId = event.processGroupId,
            sessionId = event.sessionId,
            parentTraceePid = event.parentTraceePid,
            parentTraceeVpid = event.parentTraceeVpid,
            createdAtMs = event.timestampMs,
            lastEventAtMs = event.timestampMs,
            lastEventType = event.eventType,
            lastSourceHook = event.sourceHook,
            lastCostLevel = event.costLevel,
            executable = event.executable,
            argvHash = event.argvHash,
            argvPreview = event.argvPreview,
            cwd = event.cwd,
            kfRuntimeId = event.kfRuntimeId,
            kfUnitId = event.kfUnitId,
            exitCode = event.exitCode,
            signal = event.signal,
            eventSeq = event.eventSeq,
            lifecycleSeq = event.lifecycleSeq,
            startTimeTicks = event.startTimeTicks,
            parentLifecycleSeq = event.parentLifecycleSeq,
        )
    }

    private fun pushRecentEvent(event: ProotTelemetryEvent) {
        recentEvents.addLast(event)
        while (recentEvents.size > MAX_RECENT_EVENTS) {
            recentEvents.removeFirst()
        }
    }

    private fun trimTracees() {
        if (tracees.isEmpty()) return

        val now = System.currentTimeMillis()
        val terminalCutoff = now - TERMINAL_TRACEE_TTL_MS
        val expiredTerminal = tracees.entries
            .filter { (_, record) -> !record.running && record.terminalAtMs() in 1 until terminalCutoff }
            .map { it.key }

        expiredTerminal.forEach(tracees::remove)

        val terminal = tracees.entries
            .filter { (_, record) -> !record.running }
            .sortedWith(
                compareBy<Map.Entry<TraceeLifecycleKey, ProotTraceeRecord>> { it.value.terminalAtMs() }
                    .thenBy { it.value.traceePid }
            )
        if (terminal.size <= MAX_TERMINAL_TRACEE_RECORDS) return

        val overflowTerminal = terminal
            .take(terminal.size - MAX_TERMINAL_TRACEE_RECORDS)
            .map { it.key }
        overflowTerminal.forEach(tracees::remove)
    }

    private fun buildSnapshot(
        file: File,
        collectionStatus: String,
        lastRefreshEvents: Int,
        lastRefreshForkExecEvents: Int
    ): ProotTelemetrySnapshot {
        val now = System.currentTimeMillis()
        val lastEventAt = recentEvents.lastOrNull()?.timestampMs
            ?: if (counters.totalEvents == 0L) 0L else _snapshot.value.lastEventAtMs
        val traceeList = tracees.values
            .sortedWith(
                compareBy<ProotTraceeRecord> { !it.running }
                    .thenByDescending { it.lastEventAtMs }
                    .thenBy { it.traceePid }
            )
        val liveTable = buildProcessLiveTable(
            traceeList = traceeList,
            generatedAtMs = now,
            sourceStatus = collectionStatus,
            sourcePath = file.absolutePath
        )
        val pressureWindow = buildPressureWindow(
            now = now,
            liveTraceeCount = liveTable.liveTraceeCount
        )
        val ownerIndex = buildOwnerProcessIndex(
            liveTable = liveTable,
            generatedAtMs = now,
            sourceStatus = collectionStatus
        )
        val workloadScopeIndex = buildWorkloadScopeIndex(
            liveTable = liveTable,
            generatedAtMs = now,
            sourceStatus = collectionStatus,
        )
        return ProotTelemetrySnapshot(
            sourcePath = file.absolutePath,
            collectionStatus = collectionStatus,
            fileExists = file.exists(),
            fileSizeBytes = file.length().takeIf { file.exists() } ?: 0L,
            fileLastModifiedMs = file.lastModified().takeIf { file.exists() } ?: 0L,
            refreshedAtMs = now,
            lastEventAtMs = lastEventAt,
            lastReadOffsetBytes = lastOffsetBytes,
            ownerEvidenceCompleteFromMs = ownerEvidenceCompleteFromMs,
            ownerEvidenceCoverageReason = ownerEvidenceCoverageReason,
            activeRegistryStatus = if (activeRegistryRecoveryPending) {
                "recovery_pending_${latestActiveRegistry.status.name.lowercase()}"
            } else {
                latestActiveRegistry.status.name.lowercase()
            },
            activeRegistryRootPath = latestActiveRegistry.rootPath,
            activeRegistrySessionCount = latestActiveRegistry.sessions.size,
            activeRegistryTraceeCount = latestActiveRegistry.activeTraceeCount,
            activeRegistryUnstableSessions = latestActiveRegistry.unstableSessionIds,
            activeRegistryReconciledAtMs = activeRegistryReconciledAtMs,
            lastRefreshEvents = lastRefreshEvents,
            lastRefreshForkExecEvents = lastRefreshForkExecEvents,
            probeDeclaredTargetLiveTracees = probeDeclaredTargetLiveTracees,
            liveTraceeCount = traceeList.count { it.running },
            knownTraceeCount = traceeList.size,
            counters = counters,
            recentEvents = recentEvents.toList(),
            tracees = traceeList,
            processLiveTable = liveTable,
            ownerProcessIndex = ownerIndex,
            workloadScopeIndex = workloadScopeIndex,
            pressureWindow = pressureWindow
        )
    }

    private fun buildProcessLiveTable(
        traceeList: List<ProotTraceeRecord>,
        generatedAtMs: Long,
        sourceStatus: String,
        sourcePath: String
    ): ProotProcessLiveTable {
        val workloadScopeIds = ProotWorkloadScopeProjector.project(traceeList)
        val workloadLauncherIds = ProotWorkloadScopeProjector.launcherLifecycleIds(
            records = traceeList,
            scopeIds = workloadScopeIds,
        )
        val entries = traceeList.map { record ->
            ProotLiveProcessEntry(
                prootPid = record.prootPid,
                telemetrySessionId = record.telemetrySessionId,
                prootStartMs = record.prootStartMs,
                traceePid = record.traceePid,
                traceeVpid = record.traceeVpid,
                processGroupId = record.processGroupId,
                sessionId = record.sessionId,
                parentTraceePid = record.parentTraceePid,
                parentTraceeVpid = record.parentTraceeVpid,
                state = record.liveState,
                createdAtMs = record.createdAtMs,
                lastSeenAtMs = record.lastEventAtMs,
                exitedAtMs = record.exitedAtMs,
                signaledAtMs = record.signaledAtMs,
                lastEventType = record.lastEventType,
                lastSourceHook = record.lastSourceHook,
                lastCostLevel = record.lastCostLevel,
                executable = record.executable,
                argvHash = record.argvHash,
                argvPreview = record.argvPreview,
                cwd = record.cwd,
                kfRuntimeId = record.kfRuntimeId,
                kfUnitId = record.kfUnitId,
                workloadScopeId = workloadScopeIds[record.lifecycleId].orEmpty(),
                isWorkloadLauncher = record.lifecycleId in workloadLauncherIds,
                execCount = record.execCount,
                childEventCount = record.childEventCount,
                exitCode = record.exitCode,
                signal = record.signal,
                eventSeq = record.eventSeq,
                lifecycleSeq = record.lifecycleSeq,
                startTimeTicks = record.startTimeTicks,
                parentLifecycleSeq = record.parentLifecycleSeq,
                kernelState = record.kernelState,
                verificationStatus = record.verificationStatus,
                verifiedAtElapsedMs = record.verifiedAtElapsedMs,
                evidenceSource = record.evidenceSource,
            )
        }
        return ProotProcessLiveTable(
            generatedAtMs = generatedAtMs,
            sourceStatus = sourceStatus,
            sourcePath = sourcePath,
            terminalRetentionMaxEntries = MAX_TERMINAL_TRACEE_RECORDS,
            terminalRetentionTtlMs = TERMINAL_TRACEE_TTL_MS,
            liveTraceeCount = entries.count { it.state == ProotLiveProcessState.RUNNING },
            knownTraceeCount = entries.size,
            exitedTraceeCount = entries.count { it.state == ProotLiveProcessState.EXITED },
            signaledTraceeCount = entries.count { it.state == ProotLiveProcessState.SIGNALED },
            entries = entries
        )
    }

    private fun buildOwnerProcessIndex(
        liveTable: ProotProcessLiveTable,
        generatedAtMs: Long,
        sourceStatus: String
    ): ProotOwnerProcessIndex {
        val liveEntries = liveTable.entries
            .filter { it.state == ProotLiveProcessState.RUNNING && it.kfRuntimeId.isNotBlank() }
        val groups = liveEntries
            .groupBy { it.kfRuntimeId }
            .toSortedMap()
            .map { (ownerId, entries) ->
                ProotOwnerProcessGroup(
                    ownerId = ownerId,
                    unitIds = entries.mapNotNull { it.kfUnitId.takeIf(String::isNotBlank) }.distinct().sorted(),
                    prootPids = entries.map { it.prootPid }.filter { it > 0 }.distinct().sorted(),
                    telemetrySessionIds = entries.mapNotNull { it.telemetrySessionId.takeIf(String::isNotBlank) }
                        .distinct()
                        .sorted(),
                    traceePids = entries.map { it.traceePid }.distinct().sorted(),
                    processGroupIds = entries.mapNotNull { it.processGroupId?.takeIf { pgid -> pgid > 1 } }
                        .distinct()
                        .sorted(),
                    sessionIds = entries.mapNotNull { it.sessionId?.takeIf { sid -> sid > 1 } }
                        .distinct()
                        .sorted(),
                    lifecycleIds = entries.map { it.lifecycleId }.distinct().sorted(),
                    processRefs = entries.map(ProotLiveProcessEntry::processRef)
                        .distinctBy(ProotProcessRef::lifecycleId)
                        .sortedBy(ProotProcessRef::lifecycleId),
                    liveTraceeCount = entries.size,
                    lastSeenAtMs = entries.maxOfOrNull { it.lastSeenAtMs } ?: 0L
                )
            }
        return ProotOwnerProcessIndex(
            generatedAtMs = generatedAtMs,
            sourceStatus = sourceStatus,
            ownerCount = groups.size,
            liveTraceeCount = liveEntries.size,
            groups = groups
        )
    }

    private fun buildPressureWindow(
        now: Long,
        liveTraceeCount: Int
    ): ProotPressureWindow {
        val windowStart = now - PRESSURE_WINDOW_MS
        val events = recentEvents.filter { it.timestampMs >= windowStart }
        val forkExecEvents = events.count { it.eventType in FORK_EXEC_EVENT_TYPES }
        val exitEvents = events.count {
            it.eventType == ProotTelemetryEventType.TraceeExited ||
                it.eventType == ProotTelemetryEventType.TraceeSignaled
        }
        val pressureScore = (events.size + forkExecEvents * 4 + liveTraceeCount * 2)
            .coerceAtMost(100)
        val level = when {
            pressureScore >= 70 || forkExecEvents >= 20 -> ProotPressureSignalLevel.BURST
            pressureScore >= 35 || forkExecEvents >= 8 -> ProotPressureSignalLevel.BUSY
            pressureScore > 0 -> ProotPressureSignalLevel.NORMAL
            else -> ProotPressureSignalLevel.QUIET
        }
        return ProotPressureWindow(
            generatedAtMs = now,
            windowMs = PRESSURE_WINDOW_MS,
            eventsInWindow = events.size,
            forkExecEventsInWindow = forkExecEvents,
            exitEventsInWindow = exitEvents,
            liveTraceeCount = liveTraceeCount,
            pressureScore = pressureScore,
            signalLevel = level
        )
    }

    private fun resetIfPathChanged(path: String) {
        if (lastPath == path) return
        resetReaderState(path)
    }

    private fun resetReaderState(path: String) {
        lastPath = path
        lastOffsetBytes = 0L
        rotationBaselineLoadedForPath = ""
        readerEpochMs = System.currentTimeMillis()
        ownerEvidenceCompleteFromMs = readerEpochMs
        ownerEvidenceCoverageReason = "reader_initializing"
        pendingPartialLine = ""
        counters = ProotTelemetryCounters()
        recentEvents.clear()
        tracees.clear()
        eventContinuity.clear()
        activeRegistryRecoveryPending = true
        latestActiveRegistry = ProotActiveRegistrySnapshot(
            status = ProotActiveRegistryReadStatus.MISSING,
            rootPath = "",
        )
        activeRegistryReconciledAtMs = 0L
    }

    private fun ProotTelemetryEvent.belongsToCurrentReaderEpoch(): Boolean {
        return timestampMs >= readerEpochMs
    }

    private fun ProotTelemetryEvent.belongsToRotationBaseline(now: Long): Boolean {
        return timestampMs in (now - ROTATED_BASELINE_EVENT_TTL_MS)..now
    }

    internal fun readTelemetryFileForTests(
        file: File,
        readerEpochMs: Long = 0L,
        activeRegistry: ProotActiveRegistrySnapshot? = null,
        activeRegistryNow: Long = System.currentTimeMillis(),
    ): ProotTelemetrySnapshot {
        return synchronized(readerLock) {
            synchronized(lock) {
                resetReaderState(file.absolutePath)
                this.readerEpochMs = readerEpochMs
            }
            val baselineResult = readRotationBaselineIfNeeded(file)
            val readResult = readNewLines(file)
            synchronized(lock) {
                baselineResult?.let { baseline ->
                    ownerEvidenceCompleteFromMs = baseline.ownerEvidenceCompleteFromMs
                    ownerEvidenceCoverageReason = baseline.ownerEvidenceCoverageReason
                    if (baseline.skippedBytes > 0L) {
                        counters = counters.copy(skippedBytes = counters.skippedBytes + baseline.skippedBytes)
                    }
                    baseline.lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        val event = parseEvent(line) ?: return@forEach
                        if (event.belongsToRotationBaseline(System.currentTimeMillis())) {
                            applyEvent(event)
                        }
                    }
                }
                if (readResult.skippedBytes > 0L) {
                    counters = counters.copy(skippedBytes = counters.skippedBytes + readResult.skippedBytes)
                    ownerEvidenceCompleteFromMs = maxOf(
                        ownerEvidenceCompleteFromMs,
                        firstEventTimestamp(readResult.lines) ?: System.currentTimeMillis()
                    )
                    ownerEvidenceCoverageReason = "incremental_tail_gap"
                    activeRegistryRecoveryPending = true
                }
                var parseErrors = 0
                readResult.lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val event = parseEvent(line)
                    if (event == null) {
                        parseErrors += 1
                        return@forEach
                    }
                    if (!event.belongsToCurrentReaderEpoch()) {
                        return@forEach
                    }
                    applyEvent(event)
                }
                if (parseErrors > 0) {
                    counters = counters.copy(parseErrors = counters.parseErrors + parseErrors)
                    ownerEvidenceCompleteFromMs = maxOf(ownerEvidenceCompleteFromMs, System.currentTimeMillis())
                    ownerEvidenceCoverageReason = "telemetry_parse_error"
                    activeRegistryRecoveryPending = true
                }
                activeRegistry?.let { reconcileActiveRegistry(it, activeRegistryNow) }
                _snapshot.value = buildSnapshot(
                    file = file,
                    collectionStatus = "loaded",
                    lastRefreshEvents = readResult.lines.size - parseErrors,
                    lastRefreshForkExecEvents = recentEvents.count { it.eventType in FORK_EXEC_EVENT_TYPES }
                )
                _snapshot.value
            }
        }
    }

    internal fun reconcileActiveRegistryForTests(
        registry: ProotActiveRegistrySnapshot,
        now: Long = System.currentTimeMillis(),
    ): ProotTelemetrySnapshot = synchronized(readerLock) {
        synchronized(lock) {
            reconcileActiveRegistry(registry, now)
            buildSnapshot(
                file = File(registry.rootPath, TELEMETRY_FILE_NAME),
                collectionStatus = "active_registry_test",
                lastRefreshEvents = 0,
                lastRefreshForkExecEvents = 0,
            )
        }
    }

    private fun buildWorkloadScopeIndex(
        liveTable: ProotProcessLiveTable,
        generatedAtMs: Long,
        sourceStatus: String,
    ): ProotWorkloadScopeIndex {
        val liveEntries = liveTable.entries.filter { entry ->
            entry.state == ProotLiveProcessState.RUNNING && entry.workloadScopeId.isNotBlank()
        }
        val groups = liveEntries
            .groupBy(ProotLiveProcessEntry::workloadScopeId)
            .toSortedMap()
            .map { (scopeId, entries) ->
                ProotWorkloadScopeGroup(
                    workloadScopeId = scopeId,
                    rootLifecycleId = scopeId.removePrefix("workload:"),
                    ownerIds = entries.mapNotNull { it.kfRuntimeId.takeIf(String::isNotBlank) }
                        .distinct()
                        .sorted(),
                    unitIds = entries.mapNotNull { it.kfUnitId.takeIf(String::isNotBlank) }
                        .distinct()
                        .sorted(),
                    telemetrySessionIds = entries.mapNotNull {
                        it.telemetrySessionId.takeIf(String::isNotBlank)
                    }.distinct().sorted(),
                    lifecycleIds = entries.map(ProotLiveProcessEntry::lifecycleId).distinct().sorted(),
                    processRefs = entries.map(ProotLiveProcessEntry::processRef)
                        .distinctBy(ProotProcessRef::lifecycleId)
                        .sortedBy(ProotProcessRef::lifecycleId),
                    liveTraceeCount = entries.size,
                    lastSeenAtMs = entries.maxOfOrNull(ProotLiveProcessEntry::lastSeenAtMs) ?: 0L,
                )
            }
        return ProotWorkloadScopeIndex(
            generatedAtMs = generatedAtMs,
            sourceStatus = sourceStatus,
            scopeCount = groups.size,
            liveTraceeCount = liveEntries.size,
            groups = groups,
        )
    }

    private fun uniqueHistoryArchiveFile(file: File, now: Long): File {
        val parent = file.parentFile ?: File(".")
        var candidate = File(parent, "${file.name}.history-contaminated.$now")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(parent, "${file.name}.history-contaminated.$now.$suffix")
            suffix += 1
        }
        return candidate
    }

    @Synchronized
    private fun clearPendingRefresh() {
        pendingRefresh = false
    }

    @Synchronized
    private fun consumePendingRefresh(): Boolean {
        val shouldRun = pendingRefresh
        pendingRefresh = false
        return shouldRun
    }

    private fun ProotTelemetryCounters.increment(type: ProotTelemetryEventType): ProotTelemetryCounters {
        return when (type) {
            ProotTelemetryEventType.ProotTelemetryStarted -> copy(totalEvents = totalEvents + 1, prootStarted = prootStarted + 1)
            ProotTelemetryEventType.TraceeCreated -> copy(totalEvents = totalEvents + 1, traceeCreated = traceeCreated + 1)
            ProotTelemetryEventType.ForkDetected -> copy(totalEvents = totalEvents + 1, forkDetected = forkDetected + 1)
            ProotTelemetryEventType.CloneDetected -> copy(totalEvents = totalEvents + 1, cloneDetected = cloneDetected + 1)
            ProotTelemetryEventType.VforkDetected -> copy(totalEvents = totalEvents + 1, vforkDetected = vforkDetected + 1)
            ProotTelemetryEventType.ExecDetected -> copy(totalEvents = totalEvents + 1, execDetected = execDetected + 1)
            ProotTelemetryEventType.TraceeExited -> copy(totalEvents = totalEvents + 1, traceeExited = traceeExited + 1)
            ProotTelemetryEventType.TraceeSignaled -> copy(totalEvents = totalEvents + 1, traceeSignaled = traceeSignaled + 1)
            ProotTelemetryEventType.Unknown -> copy(totalEvents = totalEvents + 1, unknownEvents = unknownEvents + 1)
        }
    }

    private data class ReadLinesResult(
        val lines: List<String>,
        val skippedBytes: Long
    )

    private data class BaselineReadResult(
        val lines: List<String>,
        val skippedBytes: Long,
        val ownerEvidenceCompleteFromMs: Long,
        val ownerEvidenceCoverageReason: String
    )

    private data class TraceeLifecycleKey(
        val telemetrySessionId: String,
        val prootStartMs: Long,
        val prootPid: Int,
        val traceePid: Int,
        val lifecycleSeq: Long = 0L,
        val legacyGenerationMs: Long = 0L
    ) {
        fun matches(ref: ProotProcessRef): Boolean =
            telemetrySessionId == ref.telemetrySessionId &&
                prootStartMs == ref.prootStartMs &&
                prootPid == ref.prootPid &&
                traceePid == ref.hostPid &&
                lifecycleSeq == ref.lifecycleSeq
    }

    private fun ProotTelemetryEvent.exactLifecycleKey(): TraceeLifecycleKey {
        return TraceeLifecycleKey(
            telemetrySessionId = telemetrySessionId,
            prootStartMs = prootStartMs,
            prootPid = prootPid,
            traceePid = traceePid,
            lifecycleSeq = lifecycleSeq,
        )
    }

    private fun ProotTelemetryEvent.hasExplicitLifecycleIdentity(): Boolean {
        return lifecycleSeq > 0L || telemetrySessionId.isNotBlank() || prootStartMs > 0L
    }

    private val FORK_EXEC_EVENT_TYPES = setOf(
        ProotTelemetryEventType.ForkDetected,
        ProotTelemetryEventType.CloneDetected,
        ProotTelemetryEventType.VforkDetected,
        ProotTelemetryEventType.ExecDetected
    )

    private val TRACEE_LIFECYCLE_START_EVENTS = setOf(
        ProotTelemetryEventType.TraceeCreated,
        ProotTelemetryEventType.ForkDetected,
        ProotTelemetryEventType.CloneDetected,
        ProotTelemetryEventType.VforkDetected
    )
}

private fun ProotTraceeRecord.terminalAtMs(): Long {
    return exitedAtMs ?: signaledAtMs ?: lastEventAtMs
}

private fun JSONObject.optIntOrNull(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return runCatching { optInt(name) }.getOrNull()
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return runCatching { optLong(name) }.getOrNull()
}
