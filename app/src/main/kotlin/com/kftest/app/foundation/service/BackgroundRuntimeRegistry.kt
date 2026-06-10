package com.kftest.app.foundation.service

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.runtime.RuntimeExposureScope
import com.kftest.app.foundation.runtime.RuntimeProcessUnitObservationState
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONArray
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BackgroundRuntimeRegistry {

    private const val RUNTIMES_FILE = "background-runtimes.json"
    private const val LOG_DIR = "background-runtimes"
    private const val LEGACY_PROBE_SUFFIX = "-kf-probe-loop"
    private const val LEGACY_SERVICE_PROBE_SUFFIX = "-kf-service-probe"
    private const val PROOT_CAPACITY_WORKER_INITIAL_COUNT = 1
    private const val PROOT_CAPACITY_WORKER_SPARE_COUNT = 1
    private const val PROOT_CAPACITY_WORKER_REGISTRATION_MAX = 3

    private val _entries = MutableStateFlow<List<BackgroundRuntimeRecord>>(emptyList())
    val entries: StateFlow<List<BackgroundRuntimeRecord>> = _entries

    fun builtinContainerSupervisorId(spaceId: String): String {
        return "background-$spaceId-container-supervisor"
    }

    fun builtinProotCapacityWorkerId(spaceId: String): String {
        return builtinProotCapacityWorkerId(spaceId, 1)
    }

    fun builtinProotCapacityWorkerId(spaceId: String, index: Int): String {
        return "background-$spaceId-proot-capacity-worker-${index.coerceAtLeast(1)}"
    }

    fun list(context: Context, spaceId: String? = null): List<BackgroundRuntimeRecord> {
        val records = readAll(WorkSurfaceRuntimeBridge.getRuntimeRoot(context))
            .sortedBy { it.createdAt }
        return if (spaceId.isNullOrBlank()) {
            records
        } else {
            records.filter { it.spaceId == spaceId }
        }
    }

    fun get(context: Context, runtimeId: String): BackgroundRuntimeRecord? {
        return list(context).firstOrNull { it.id == runtimeId }
    }

    @Synchronized
    fun ensureProotCapacityWorkerHeadroom(context: Context, spaceId: String) {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val existingRaw = readAll(runtimeRoot)
        val existing = normalizeAndPruneProotCapacityWorkerRecords(spaceId, existingRaw)
        val desiredCount = desiredProotCapacityWorkerCount(existing, spaceId)
        val existingIds = existing.asSequence()
            .filter { it.spaceId == spaceId && it.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER }
            .map { it.id }
            .toSet()
        val missing = (1..desiredCount)
            .map { index -> index to builtinProotCapacityWorkerId(spaceId, index) }
            .filterNot { (_, id) -> id in existingIds }
        if (missing.isEmpty() && existing == existingRaw) {
            return
        }
        val now = System.currentTimeMillis()
        val merged = LinkedHashMap<String, BackgroundRuntimeRecord>()
        existing.forEach { record -> merged[record.id] = record }
        missing.forEach { (index, id) ->
            merged[id] = buildProotCapacityWorker(context, spaceId, index, now + index)
        }
        saveRecords(runtimeRoot, merged.values.toList())
    }

    @Synchronized
    fun ensureBuiltinRuntimes(context: Context, spaceId: String) {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val existing = normalizeAndPruneProotCapacityWorkerRecords(spaceId, readAll(runtimeRoot))
        val supervisorId = builtinContainerSupervisorId(spaceId)
        val openClawId = "background-$spaceId-openclaw-gateway"
        val now = System.currentTimeMillis()
        val prootCapacityWorkers = (1..desiredProotCapacityWorkerCount(existing, spaceId)).map { index ->
            buildProotCapacityWorker(context, spaceId, index, now + index)
        }

        val builtins = listOf(
            BackgroundRuntimeRecord(
                id = supervisorId,
                spaceId = spaceId,
                kind = BackgroundRuntimeKind.CONTAINER_SUPERVISOR,
                mode = BackgroundRuntimeMode.PROCESS,
                title = "容器骨架",
                workingDirectory = WorkSurfaceRuntimeBridge.defaults.rootHomeDir,
                startCommand = "mkdir -p /run /run/supervisor /var/log/supervisor && " +
                    "if ! grep -q '^\\[inet_http_server\\]' /etc/supervisor/supervisord.conf; then " +
                    "printf '\\n[inet_http_server]\\nport=127.0.0.1:19001\\n' >> /etc/supervisor/supervisord.conf; " +
                    "fi && exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf",
                bindAddress = "127.0.0.1",
                bindPort = 19001,
                exposureScope = RuntimeExposureScope.LOOPBACK_ONLY,
                logPath = buildLogFile(context, supervisorId).absolutePath,
                createdAt = now,
                restartPolicy = BackgroundRuntimeRestartPolicy.ALWAYS_CORE,
                retentionClass = RuntimeRetentionClass.CRITICAL_CORE
            ),
            BackgroundRuntimeRecord(
                id = openClawId,
                spaceId = spaceId,
                kind = BackgroundRuntimeKind.OPENCLAW_GATEWAY,
                mode = BackgroundRuntimeMode.PROCESS,
                title = "OpenClaw 网关",
                workingDirectory = WorkSurfaceRuntimeBridge.defaults.workspaceDir,
                startCommand = "exec openclaw gateway run --bind loopback --port 18789",
                bindAddress = "127.0.0.1",
                bindPort = 18789,
                exposureScope = RuntimeExposureScope.LOOPBACK_ONLY,
                requiredCapabilities = listOf(BackgroundRuntimeCapability.MDNS),
                healthCommand = "openclaw gateway health",
                healthCheckStartupDelayMs = 6_000L,
                logPath = buildLogFile(context, openClawId).absolutePath,
                createdAt = now,
                restartPolicy = BackgroundRuntimeRestartPolicy.ON_FAILURE,
                retentionClass = RuntimeRetentionClass.RESIDENT
            )
        ) + prootCapacityWorkers

        val merged = LinkedHashMap<String, BackgroundRuntimeRecord>()
        existing
            .filterNot { record ->
                record.id.endsWith(LEGACY_PROBE_SUFFIX) || record.id.endsWith(LEGACY_SERVICE_PROBE_SUFFIX)
            }
            .forEach { record ->
                merged[record.id] = record
            }
        builtins.forEach { builtin ->
            val previous = merged[builtin.id]
            merged[builtin.id] = if (previous == null) {
                builtin
            } else {
                if (previous.statusCommand != builtin.statusCommand) {
                    Logger.i(
                        "BackgroundRuntimeRegistry",
                        "刷新内置运行项探测命令: ${builtin.id}, old=${previous.statusCommand}, new=${builtin.statusCommand}"
                    )
                }
                builtin.copy(
                    createdAt = previous.createdAt,
                    lastStartedAt = previous.lastStartedAt,
                    lastStoppedAt = previous.lastStoppedAt,
                    status = previous.status,
                    healthStatus = previous.healthStatus,
                    pid = previous.pid,
                    lastHealthSummary = previous.lastHealthSummary,
                    lastHealthCheckedAt = previous.lastHealthCheckedAt,
                    lastExitCode = previous.lastExitCode,
                    lastError = previous.lastError,
                    restartPolicy = builtin.restartPolicy,
                    restartFailureCount = previous.restartFailureCount,
                    lastRestartAt = previous.lastRestartAt,
                    nextRestartAllowedAt = previous.nextRestartAllowedAt,
                    lastRestartReason = previous.lastRestartReason,
                    lastRecoveredAt = previous.lastRecoveredAt,
                    lastRecoverySource = previous.lastRecoverySource,
                    lastRecoveryReason = previous.lastRecoveryReason,
                    lastAdmissionDeferredAt = previous.lastAdmissionDeferredAt,
                    lastAdmissionSource = previous.lastAdmissionSource,
                    lastAdmissionReason = previous.lastAdmissionReason,
                    lastReclaimedAt = previous.lastReclaimedAt,
                    lastReclaimSource = previous.lastReclaimSource,
                    lastReclaimReason = previous.lastReclaimReason,
                    lastStopReconciliationState = previous.lastStopReconciliationState,
                    lastStopReconciliationReason = previous.lastStopReconciliationReason,
                    lastStopReconciliationAt = previous.lastStopReconciliationAt,
                    lastStopReconciliationAutoRecoverySuppressed =
                        previous.lastStopReconciliationAutoRecoverySuppressed,
                    retentionClass = builtin.retentionClass
                )
            }
        }
        saveRecords(runtimeRoot, merged.values.toList())
    }

    private fun desiredProotCapacityWorkerCount(
        records: List<BackgroundRuntimeRecord>,
        spaceId: String
    ): Int {
        val highestActiveIndex = records.asSequence()
            .filter { it.spaceId == spaceId && it.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER }
            .filter { it.isActiveRuntime() }
            .mapNotNull { parseProotCapacityWorkerIndex(spaceId, it.id) }
            .maxOrNull() ?: 0
        return maxOf(
            PROOT_CAPACITY_WORKER_INITIAL_COUNT,
            highestActiveIndex + PROOT_CAPACITY_WORKER_SPARE_COUNT
        ).coerceAtMost(PROOT_CAPACITY_WORKER_REGISTRATION_MAX)
    }

    @Synchronized
    fun ensureProotCapacityWorkerSlot(
        context: Context,
        spaceId: String,
        index: Int
    ): BackgroundRuntimeRecord? {
        val safeIndex = index.coerceAtLeast(1)
        if (safeIndex > PROOT_CAPACITY_WORKER_REGISTRATION_MAX) {
            return null
        }
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val existing = readAll(runtimeRoot)
        val targetId = builtinProotCapacityWorkerId(spaceId, safeIndex)
        existing.firstOrNull { it.id == targetId }?.let { record ->
            val normalized = normalizeProotCapacityWorkerRecord(spaceId, record)
            if (normalized != record) {
                saveRecords(
                    runtimeRoot,
                    existing.map { current ->
                        if (current.id == record.id) normalized else current
                    }
                )
            }
            return normalized
        }
        val now = System.currentTimeMillis()
        val created = buildProotCapacityWorker(context, spaceId, safeIndex, now + safeIndex)
        saveRecords(runtimeRoot, existing + created)
        return created
    }

    private fun parseProotCapacityWorkerIndex(spaceId: String, runtimeId: String): Int? {
        val prefix = "background-$spaceId-proot-capacity-worker-"
        return runtimeId
            .takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
    }

    private fun buildProotCapacityWorker(
        context: Context,
        spaceId: String,
        index: Int,
        createdAt: Long
    ): BackgroundRuntimeRecord {
        val prootCapacityWorkerId = builtinProotCapacityWorkerId(spaceId, index)
        return BackgroundRuntimeRecord(
            id = prootCapacityWorkerId,
            spaceId = spaceId,
            kind = BackgroundRuntimeKind.PROOT_CAPACITY_WORKER,
            mode = BackgroundRuntimeMode.PROCESS,
            title = "PRoot 容量工作器 $index",
            workingDirectory = WorkSurfaceRuntimeBridge.defaults.workspaceDir,
            startCommand = "mkdir -p /run/kf-proot-capacity && " +
                "export KF_PROOT_CAPACITY_WORKER_ID='$prootCapacityWorkerId' && " +
                "echo \$\$ > /run/kf-proot-capacity/worker-$index.pid && " +
                "exec /bin/bash -lc 'trap \"rm -f /run/kf-proot-capacity/worker-$index.pid; exit 0\" INT TERM; " +
                "while true; do sleep 3600 & wait \$!; done'",
            statusCommand = "test -f /run/kf-proot-capacity/worker-$index.pid && " +
                "kill -0 \"\$(cat /run/kf-proot-capacity/worker-$index.pid)\" 2>/dev/null",
            healthCommand = "test -f /run/kf-proot-capacity/worker-$index.pid && " +
                "kill -0 \"\$(cat /run/kf-proot-capacity/worker-$index.pid)\" 2>/dev/null && " +
                "printf 'proot_capacity_worker_${index}_alive\\n'",
            healthCheckStartupDelayMs = 2_000L,
            exposureScope = RuntimeExposureScope.HOST_LOCAL_ONLY,
            logPath = buildLogFile(context, prootCapacityWorkerId).absolutePath,
            createdAt = createdAt,
            restartPolicy = BackgroundRuntimeRestartPolicy.NEVER,
            retentionClass = prootCapacityWorkerRetentionClass(index)
        )
    }

    private fun normalizeProotCapacityWorkerRecord(
        spaceId: String,
        record: BackgroundRuntimeRecord
    ): BackgroundRuntimeRecord {
        if (record.spaceId != spaceId || record.kind != BackgroundRuntimeKind.PROOT_CAPACITY_WORKER) {
            return record
        }
        val index = parseProotCapacityWorkerIndex(spaceId, record.id) ?: 1
        return record.copy(
            restartPolicy = BackgroundRuntimeRestartPolicy.NEVER,
            retentionClass = prootCapacityWorkerRetentionClass(index)
        )
    }

    internal fun normalizeAndPruneProotCapacityWorkerRecords(
        spaceId: String,
        records: List<BackgroundRuntimeRecord>
    ): List<BackgroundRuntimeRecord> {
        return records.mapNotNull { record ->
            if (record.spaceId != spaceId || record.kind != BackgroundRuntimeKind.PROOT_CAPACITY_WORKER) {
                return@mapNotNull record
            }
            val index = parseProotCapacityWorkerIndex(spaceId, record.id)
            if (
                index != null &&
                index > PROOT_CAPACITY_WORKER_REGISTRATION_MAX &&
                !record.isActiveRuntime()
            ) {
                return@mapNotNull null
            }
            normalizeProotCapacityWorkerRecord(spaceId, record)
        }
    }

    private fun prootCapacityWorkerRetentionClass(index: Int): RuntimeRetentionClass {
        return if (index <= 1) {
            RuntimeRetentionClass.RESIDENT
        } else {
            RuntimeRetentionClass.EPHEMERAL
        }
    }

    @Synchronized
    fun upsert(context: Context, record: BackgroundRuntimeRecord): BackgroundRuntimeRecord {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot)
            .filterNot { it.id == record.id }
            .toMutableList()
            .apply { add(record) }
        saveRecords(runtimeRoot, updated)
        return record
    }

    @Synchronized
    fun updateStatus(
        context: Context,
        runtimeId: String,
        status: BackgroundRuntimeStatus,
        pid: Int? = null,
        lastExitCode: Int? = null,
        lastError: String? = null
    ): BackgroundRuntimeRecord? {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot).map { record ->
            if (record.id == runtimeId) {
                record.copy(
                    status = status,
                    pid = pid,
                    lastExitCode = lastExitCode ?: record.lastExitCode,
                    lastStartedAt = if (status.isActiveStatus()) {
                        if (record.status.isActiveStatus() && record.lastStartedAt != null) {
                            record.lastStartedAt
                        } else {
                            System.currentTimeMillis()
                        }
                    } else {
                        record.lastStartedAt
                    },
                    lastStoppedAt = if (status.isTerminalStatus()) {
                        if (record.status.isTerminalStatus() && record.lastStoppedAt != null) {
                            record.lastStoppedAt
                        } else {
                            System.currentTimeMillis()
                        }
                    } else {
                        record.lastStoppedAt
                    },
                    lastError = lastError,
                    lastStopReconciliationState = if (status.isActiveStatus()) {
                        null
                    } else {
                        record.lastStopReconciliationState
                    },
                    lastStopReconciliationReason = if (status.isActiveStatus()) {
                        null
                    } else {
                        record.lastStopReconciliationReason
                    },
                    lastStopReconciliationAt = if (status.isActiveStatus()) {
                        null
                    } else {
                        record.lastStopReconciliationAt
                    },
                    lastStopReconciliationAutoRecoverySuppressed = if (status.isActiveStatus()) {
                        false
                    } else {
                        record.lastStopReconciliationAutoRecoverySuppressed
                    }
                )
            } else {
                record
            }
        }
        saveRecords(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    @Synchronized
    fun updateHealth(
        context: Context,
        runtimeId: String,
        healthStatus: BackgroundRuntimeHealthStatus,
        lastHealthSummary: String? = null,
        lastHealthCheckedAt: Long? = null
    ): BackgroundRuntimeRecord? {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot).map { record ->
            if (record.id == runtimeId) {
                record.copy(
                    healthStatus = healthStatus,
                    lastHealthSummary = lastHealthSummary,
                    lastHealthCheckedAt = lastHealthCheckedAt
                )
            } else {
                record
            }
        }
        saveRecords(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    @Synchronized
    fun updateRestartState(
        context: Context,
        runtimeId: String,
        restartFailureCount: Int? = null,
        lastRestartAt: Long? = null,
        nextRestartAllowedAt: Long? = null,
        lastRestartReason: String? = null,
        clearBackoff: Boolean = false
    ): BackgroundRuntimeRecord? {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot).map { record ->
            if (record.id == runtimeId) {
                record.copy(
                    restartFailureCount = restartFailureCount ?: record.restartFailureCount,
                    lastRestartAt = lastRestartAt ?: record.lastRestartAt,
                    nextRestartAllowedAt = if (clearBackoff) null else nextRestartAllowedAt ?: record.nextRestartAllowedAt,
                    lastRestartReason = if (clearBackoff && lastRestartReason == null) {
                        null
                    } else {
                        lastRestartReason ?: record.lastRestartReason
                    }
                )
            } else {
                record
            }
        }
        saveRecords(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    @Synchronized
    fun updateStopReconciliationState(
        context: Context,
        runtimeId: String,
        state: RuntimeProcessUnitObservationState,
        reason: String,
        autoRecoverySuppressed: Boolean,
        reconciledAt: Long = System.currentTimeMillis()
    ): BackgroundRuntimeRecord? {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot).map { record ->
            if (record.id == runtimeId) {
                record.copy(
                    lastStopReconciliationState = state,
                    lastStopReconciliationReason = reason,
                    lastStopReconciliationAt = reconciledAt,
                    lastStopReconciliationAutoRecoverySuppressed = autoRecoverySuppressed
                )
            } else {
                record
            }
        }
        saveRecords(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    @Synchronized
    fun updateRecoveryState(
        context: Context,
        runtimeId: String,
        recoveredAt: Long,
        recoverySource: String,
        recoveryReason: String? = null
    ): BackgroundRuntimeRecord? {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot).map { record ->
            if (record.id == runtimeId) {
                record.copy(
                    lastRecoveredAt = recoveredAt,
                    lastRecoverySource = recoverySource,
                    lastRecoveryReason = recoveryReason
                )
            } else {
                record
            }
        }
        saveRecords(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    @Synchronized
    fun updateReclaimState(
        context: Context,
        runtimeId: String,
        reclaimedAt: Long,
        reclaimSource: String,
        reclaimReason: String? = null
    ): BackgroundRuntimeRecord? {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot).map { record ->
            if (record.id == runtimeId) {
                record.copy(
                    lastReclaimedAt = reclaimedAt,
                    lastReclaimSource = reclaimSource,
                    lastReclaimReason = reclaimReason
                )
            } else {
                record
            }
        }
        saveRecords(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    @Synchronized
    fun updateAdmissionState(
        context: Context,
        runtimeId: String,
        deferredAt: Long,
        admissionSource: String,
        admissionReason: String
    ): BackgroundRuntimeRecord? {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot).map { record ->
            if (record.id == runtimeId) {
                record.copy(
                    lastAdmissionDeferredAt = deferredAt,
                    lastAdmissionSource = admissionSource,
                    lastAdmissionReason = admissionReason
                )
            } else {
                record
            }
        }
        saveRecords(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    @Synchronized
    fun clearAdmissionState(
        context: Context,
        runtimeId: String
    ): BackgroundRuntimeRecord? {
        val runtimeRoot = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)
        val updated = readAll(runtimeRoot).map { record ->
            if (record.id == runtimeId) {
                record.copy(
                    lastAdmissionDeferredAt = null,
                    lastAdmissionSource = null,
                    lastAdmissionReason = null
                )
            } else {
                record
            }
        }
        saveRecords(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    fun buildLogFile(context: Context, runtimeId: String): File {
        val logsDir = File(WorkSurfaceRuntimeBridge.getLogsDir(context), LOG_DIR)
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        return File(logsDir, "$runtimeId.log")
    }

    fun snapshot(spaceId: String? = null): List<BackgroundRuntimeRecord> {
        return if (spaceId.isNullOrBlank()) {
            _entries.value
        } else {
            _entries.value.filter { it.spaceId == spaceId }
        }
    }

    fun refreshFromDisk(context: Context): List<BackgroundRuntimeRecord> {
        return readAll(WorkSurfaceRuntimeBridge.getRuntimeRoot(context))
    }

    private fun readAll(runtimeRoot: File): List<BackgroundRuntimeRecord> {
        val records = loadRecords(runtimeRoot).sortedBy { it.createdAt }
        publish(records)
        return records
    }

    private fun publish(records: List<BackgroundRuntimeRecord>) {
        _entries.value = records.sortedBy { it.createdAt }
    }

    private fun loadRecords(runtimeRoot: File): List<BackgroundRuntimeRecord> {
        val target = File(runtimeRoot, RUNTIMES_FILE)
        if (!target.exists()) {
            publish(emptyList())
            return emptyList()
        }

        return runCatching {
            val raw = target.readText()
            if (raw.isBlank()) {
                emptyList()
            } else {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        add(BackgroundRuntimeRecord.fromJson(array.getJSONObject(index)))
                    }
                }
            }
        }.getOrElse { error ->
            Logger.e("BackgroundRuntimeRegistry", "读取后台运行项失败: ${error.message}")
            emptyList()
        }
    }

    private fun saveRecords(runtimeRoot: File, records: List<BackgroundRuntimeRecord>) {
        val target = File(runtimeRoot, RUNTIMES_FILE)
        val array = JSONArray()
        records.sortedBy { it.createdAt }.forEach { array.put(it.toJson()) }
        target.parentFile?.mkdirs()
        target.writeText(array.toString(2))
        publish(records)
    }
}
