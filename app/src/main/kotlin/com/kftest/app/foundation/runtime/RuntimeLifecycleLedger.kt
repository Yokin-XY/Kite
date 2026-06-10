package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kftest.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Per-KF-session lifecycle ledger.
 *
 * Process tables describe facts. This ledger describes lifecycle judgment: when each PID entered
 * the lease ledger, why it was renewed or not renewed, when it expired, and what KO action was
 * taken. It is intentionally bounded so the system can keep evidence without growing forever.
 */
object RuntimeLifecycleLedgerStore {
    private const val HEADER =
        "session_id\tevent_at_ms\tevent_type\tpid\tproot_pid\tppid\tpgid\tsid\t" +
            "unit_id\tworkload_id\ttitle\tcommand\tlayer\ttier\tretention\tactivity_state\t" +
            "disposition\tlease_first_seen_at_ms\tlease_expire_at_ms\tlease_remaining_ms\t" +
            "lease_extension_ms\texpired_grace_active\texpired_grace_remaining_ms\t" +
            "activity_score_percent\trss_kb\tcpu_time_ticks\tprocess_count\treclaim_rank\t" +
            "action\tsignal\ttarget_mode\tresult\treason\thard_conditions\n"

    private const val MAX_SESSION_FILES = 8
    private const val MAX_LEDGER_BYTES = 2L * 1024L * 1024L
    private const val SNAPSHOT_MIN_INTERVAL_MS = 30_000L

    private val sessionStartedAtMs = System.currentTimeMillis()
    private val sessionId = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        .format(Date(sessionStartedAtMs))

    private val initializedWorkspaces = mutableSetOf<String>()
    private val lastSnapshotAtByWorkspace = mutableMapOf<String, Long>()
    private val lastSnapshotSignatureByWorkspace = mutableMapOf<String, String>()

    @Synchronized
    fun recordSnapshot(
        workspacePath: String?,
        roots: List<RuntimeRootSnapshot>,
        reclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ) {
        val workspaceDir = workspacePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return
        recordSnapshot(
            workspaceDir = workspaceDir,
            roots = roots,
            reclaimPlan = reclaimPlan,
            now = now
        )
    }

    @Synchronized
    fun recordSnapshot(
        workspaceDir: File,
        roots: List<RuntimeRootSnapshot>,
        reclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ) {
        val files = prepareFiles(workspaceDir)
        val workspaceKey = workspaceDir.absolutePath
        val signature = reclaimPlan.items.joinToString(";") { item ->
            "${item.workloadId}:${item.activityState}:${item.disposition}:${item.leaseExpireAtMs}:" +
                "${item.leaseRemainingMs}:${item.leaseExtensionMs}:${item.activityScorePercent}:" +
                "${item.reclaimRank}"
        }
        val lastAt = lastSnapshotAtByWorkspace[workspaceKey] ?: 0L
        val lastSignature = lastSnapshotSignatureByWorkspace[workspaceKey]
        if (now - lastAt < SNAPSHOT_MIN_INTERVAL_MS && signature == lastSignature) {
            return
        }
        lastSnapshotAtByWorkspace[workspaceKey] = now
        lastSnapshotSignatureByWorkspace[workspaceKey] = signature

        val rootsByKey = roots.associateBy { it.ownershipKey }
        val lines = reclaimPlan.items.map { item ->
            val root = rootsByKey[item.workloadId] ?: roots.firstOrNull { root ->
                root.ownerId == item.workloadId || root.processUnitId == item.workloadId
            }
            lineFor(
                eventAtMs = now,
                eventType = "settlement",
                root = root,
                item = item,
                action = if (item.reclaimRank > 0) "eligible_for_ko" else "none",
                signal = "none",
                targetMode = "none",
                result = item.disposition.name,
                reason = item.reason,
                hardConditions = item.activityReason
            )
        }
        appendLines(files, lines)
    }

    @Synchronized
    fun recordAction(
        context: Context?,
        root: RuntimeRootSnapshot?,
        item: RuntimeLifecycleReclaimItem?,
        unitId: String,
        pid: Int?,
        action: String,
        signal: String,
        targetMode: String,
        result: String,
        reason: String,
        hardConditions: String,
        now: Long = System.currentTimeMillis()
    ) {
        val workspaceDir = context
            ?.applicationContext
            ?.let { WorkSurfaceRuntimeBridge.getSavedContainer(it) }
            ?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return
        recordAction(
            workspaceDir = workspaceDir,
            root = root,
            item = item,
            unitId = unitId,
            pid = pid,
            action = action,
            signal = signal,
            targetMode = targetMode,
            result = result,
            reason = reason,
            hardConditions = hardConditions,
            now = now
        )
    }

    @Synchronized
    fun recordAction(
        workspaceDir: File,
        root: RuntimeRootSnapshot?,
        item: RuntimeLifecycleReclaimItem?,
        unitId: String,
        pid: Int?,
        action: String,
        signal: String,
        targetMode: String,
        result: String,
        reason: String,
        hardConditions: String,
        now: Long = System.currentTimeMillis()
    ) {
        val files = prepareFiles(workspaceDir)
        val fallbackRoot = root ?: pid?.let { pidValue ->
            RuntimeRootSnapshot(
                ownerKind = RuntimeRootOwnerKind.UNATTRIBUTED,
                ownerId = pidValue.toString(),
                title = "pid-$pidValue",
                statusLabel = "observed",
                observedPid = pidValue
            )
        }
        val line = lineFor(
            eventAtMs = now,
            eventType = "action",
            root = fallbackRoot,
            item = item,
            unitIdOverride = unitId,
            action = action,
            signal = signal,
            targetMode = targetMode,
            result = result,
            reason = reason,
            hardConditions = hardConditions
        )
        appendLines(files, listOf(line))
    }

    @Synchronized
    fun resetForTests() {
        initializedWorkspaces.clear()
        lastSnapshotAtByWorkspace.clear()
        lastSnapshotSignatureByWorkspace.clear()
    }

    private fun prepareFiles(workspaceDir: File): LedgerFiles {
        val currentFile = WorkspaceBuildSupport.runtimeLifecycleLedgerFile(workspaceDir)
        val historyDir = WorkspaceBuildSupport.runtimeLifecycleLedgerDir(workspaceDir)
        val sessionFile = File(historyDir, "runtime-lifecycle-ledger-$sessionId.tsv")
        listOfNotNull(currentFile.parentFile, historyDir).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
        val workspaceKey = workspaceDir.absolutePath
        if (initializedWorkspaces.add(workspaceKey)) {
            currentFile.writeText(HEADER)
            if (!sessionFile.exists()) {
                sessionFile.writeText(HEADER)
            }
            pruneSessionFiles(historyDir)
        } else if (!currentFile.exists()) {
            currentFile.writeText(HEADER)
        }
        if (!sessionFile.exists()) {
            sessionFile.writeText(HEADER)
        }
        return LedgerFiles(currentFile, sessionFile)
    }

    private fun appendLines(files: LedgerFiles, lines: List<String>) {
        if (lines.isEmpty()) return
        val text = lines.joinToString(separator = "\n", postfix = "\n")
        files.current.appendText(text)
        files.session.appendText(text)
        trimFile(files.current)
        trimFile(files.session)
    }

    private fun lineFor(
        eventAtMs: Long,
        eventType: String,
        root: RuntimeRootSnapshot?,
        item: RuntimeLifecycleReclaimItem?,
        unitIdOverride: String? = null,
        action: String,
        signal: String,
        targetMode: String,
        result: String,
        reason: String,
        hardConditions: String
    ): String {
        val pid = root?.observedPid ?: root?.rootPid ?: root?.expectedPid ?: 0
        val unitId = unitIdOverride
            ?: item?.workloadId
            ?: root?.processUnitId
            ?: root?.ownershipKey
            ?: "none"
        val workloadId = item?.workloadId ?: root?.ownershipKey ?: unitId
        val values = listOf(
            sessionId,
            eventAtMs.toString(),
            eventType,
            pid.toString(),
            (root?.prootPid ?: 0).toString(),
            (root?.parentPid ?: 0).toString(),
            (root?.rootProcessGroupId ?: 0).toString(),
            (root?.rootSessionId ?: 0).toString(),
            unitId,
            workloadId,
            root?.title ?: "unknown",
            root?.commandLine ?: "",
            item?.layer?.name ?: "none",
            item?.tier?.name ?: "none",
            item?.retention?.name ?: root?.retentionClass?.name ?: "none",
            item?.activityState?.name ?: "none",
            item?.disposition?.name ?: "none",
            (item?.leaseFirstSeenAtMs ?: root?.lastStartedAt ?: root?.lastSeenAt ?: 0L).toString(),
            (item?.leaseExpireAtMs ?: 0L).toString(),
            (item?.leaseRemainingMs ?: 0L).toString(),
            (item?.leaseExtensionMs ?: 0L).toString(),
            (item?.leaseExpiredGraceActive ?: false).toString(),
            (item?.leaseExpiredGraceRemainingMs ?: 0L).toString(),
            (item?.activityScorePercent ?: 0).toString(),
            (item?.rssKb ?: root?.rssKb ?: 0L).toString(),
            (root?.cpuTimeTicks ?: 0L).toString(),
            (item?.processCount ?: root?.processCount ?: 0).toString(),
            (item?.reclaimRank ?: 0).toString(),
            action,
            signal,
            targetMode,
            result,
            reason,
            hardConditions
        )
        return values.joinToString("\t") { it.toTsvValue() }
    }

    private fun trimFile(file: File) {
        if (!file.exists() || file.length() <= MAX_LEDGER_BYTES) return
        val lines = file.readLines()
        if (lines.size <= 2) return
        val header = lines.first()
        val rows = lines.drop(1)
        var totalBytes = header.length + 1
        val kept = ArrayDeque<String>()
        for (line in rows.asReversed()) {
            val lineBytes = line.toByteArray().size + 1
            if (totalBytes + lineBytes > MAX_LEDGER_BYTES) break
            kept.addFirst(line)
            totalBytes += lineBytes
        }
        file.writeText(buildString {
            appendLine(header)
            kept.forEach { appendLine(it) }
        })
    }

    private fun pruneSessionFiles(dir: File) {
        if (!dir.exists()) return
        val files = dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith("runtime-lifecycle-ledger-") &&
                file.name.endsWith(".tsv")
        }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_SESSION_FILES).forEach { it.delete() }
    }

    private fun String.toTsvValue(): String {
        return replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .let { value ->
                if (value.length <= 240) value else value.take(237) + "..."
            }
    }

    private data class LedgerFiles(
        val current: File,
        val session: File
    )
}
