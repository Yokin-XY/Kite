package com.kite.app.foundation.runtime

import java.io.File

/**
 * PRoot tracee 的稳定引用。PID 只是执行句柄，真正身份由 PRoot 会话和生命周期编号决定。
 */
data class ProotProcessRef(
    val telemetrySessionId: String,
    val prootStartMs: Long,
    val prootPid: Int,
    val lifecycleSeq: Long,
    val hostPid: Int,
    val guestPid: Long,
    val startTimeTicks: Long,
) {
    val lifecycleId: String
        get() = listOf(
            telemetrySessionId.ifBlank { "legacy" },
            prootStartMs,
            prootPid,
            lifecycleSeq.takeIf { it > 0L } ?: guestPid,
            hostPid,
        ).joinToString(":")

    val hasStrongIdentity: Boolean
        get() = telemetrySessionId.isNotBlank() &&
            prootStartMs > 0L &&
            prootPid > 1 &&
            lifecycleSeq > 0L &&
            hostPid > 1 &&
            startTimeTicks > 0L
}

enum class ProotKernelProcessState {
    RUNNING,
    SLEEPING,
    DISK_SLEEP,
    STOPPED,
    TRACING_STOP,
    ZOMBIE,
    DEAD,
    IDLE,
    UNKNOWN,
}

enum class ProotProcessVerificationStatus {
    MATCHED_ACTIVE,
    MATCHED_ZOMBIE,
    MISSING,
    PID_REUSED,
    UNREADABLE,
}

data class ProotProcessVerification(
    val ref: ProotProcessRef,
    val status: ProotProcessVerificationStatus,
    val kernelState: ProotKernelProcessState = ProotKernelProcessState.UNKNOWN,
    val parentPid: Int? = null,
    val processGroupId: Int? = null,
    val sessionId: Int? = null,
    val observedStartTimeTicks: Long? = null,
    val verifiedAtElapsedMs: Long,
    val reason: String,
) {
    val identityMatched: Boolean
        get() = status == ProotProcessVerificationStatus.MATCHED_ACTIVE ||
            status == ProotProcessVerificationStatus.MATCHED_ZOMBIE

    val terminal: Boolean
        get() = status == ProotProcessVerificationStatus.MISSING ||
            status == ProotProcessVerificationStatus.PID_REUSED
}

internal data class LinuxProcStat(
    val pid: Int,
    val command: String,
    val state: ProotKernelProcessState,
    val parentPid: Int,
    val processGroupId: Int,
    val sessionId: Int,
    val startTimeTicks: Long,
)

/** 只解析单个 `/proc/<pid>/stat`，不承担目录扫描。 */
internal object LinuxProcStatParser {
    fun parse(raw: String): LinuxProcStat? {
        val line = raw.trim()
        val commandStart = line.indexOf('(')
        val commandEnd = line.lastIndexOf(')')
        if (commandStart <= 0 || commandEnd <= commandStart) return null

        val pid = line.substring(0, commandStart).trim().toIntOrNull() ?: return null
        val command = line.substring(commandStart + 1, commandEnd)
        val tail = line.substring(commandEnd + 1).trim().split(Regex("\\s+"))
        // tail[0] 是字段 3 state；starttime 是字段 22，因此位于 tail[19]。
        if (tail.size <= 19) return null
        val state = tail[0].singleOrNull()?.toKernelState() ?: ProotKernelProcessState.UNKNOWN
        return LinuxProcStat(
            pid = pid,
            command = command,
            state = state,
            parentPid = tail[1].toIntOrNull() ?: return null,
            processGroupId = tail[2].toIntOrNull() ?: return null,
            sessionId = tail[3].toIntOrNull() ?: return null,
            startTimeTicks = tail[19].toLongOrNull() ?: return null,
        )
    }

    private fun Char.toKernelState(): ProotKernelProcessState = when (this) {
        'R' -> ProotKernelProcessState.RUNNING
        'S' -> ProotKernelProcessState.SLEEPING
        'D' -> ProotKernelProcessState.DISK_SLEEP
        'T' -> ProotKernelProcessState.STOPPED
        't' -> ProotKernelProcessState.TRACING_STOP
        'Z' -> ProotKernelProcessState.ZOMBIE
        'X', 'x' -> ProotKernelProcessState.DEAD
        'I' -> ProotKernelProcessState.IDLE
        else -> ProotKernelProcessState.UNKNOWN
    }
}

/**
 * Android 控制面的定向核验器。调用方必须在后台线程执行；本类永远不会枚举 `/proc`。
 */
internal class ProotProcessVerifier(
    private val procRoot: File = File("/proc"),
    private val elapsedRealtimeMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    fun verify(ref: ProotProcessRef): ProotProcessVerification {
        if (ref.hostPid <= 1) {
            return result(ref, ProotProcessVerificationStatus.UNREADABLE, "invalid_host_pid")
        }

        val statFile = File(File(procRoot, ref.hostPid.toString()), "stat")
        if (!statFile.exists()) {
            return result(ref, ProotProcessVerificationStatus.MISSING, "proc_stat_missing")
        }

        val stat = runCatching { LinuxProcStatParser.parse(statFile.readText()) }
            .getOrNull()
            ?: return result(ref, ProotProcessVerificationStatus.UNREADABLE, "proc_stat_unreadable")

        if (stat.pid != ref.hostPid) {
            return result(
                ref,
                ProotProcessVerificationStatus.PID_REUSED,
                "proc_pid_mismatch",
                stat,
            )
        }
        if (ref.startTimeTicks > 0L && stat.startTimeTicks != ref.startTimeTicks) {
            return result(
                ref,
                ProotProcessVerificationStatus.PID_REUSED,
                "proc_start_time_mismatch",
                stat,
            )
        }

        val status = if (stat.state == ProotKernelProcessState.ZOMBIE) {
            ProotProcessVerificationStatus.MATCHED_ZOMBIE
        } else {
            ProotProcessVerificationStatus.MATCHED_ACTIVE
        }
        return result(ref, status, "identity_matched", stat)
    }

    private fun result(
        ref: ProotProcessRef,
        status: ProotProcessVerificationStatus,
        reason: String,
        stat: LinuxProcStat? = null,
    ): ProotProcessVerification = ProotProcessVerification(
        ref = ref,
        status = status,
        kernelState = stat?.state ?: ProotKernelProcessState.UNKNOWN,
        parentPid = stat?.parentPid,
        processGroupId = stat?.processGroupId,
        sessionId = stat?.sessionId,
        observedStartTimeTicks = stat?.startTimeTicks,
        verifiedAtElapsedMs = elapsedRealtimeMs(),
        reason = reason,
    )
}

enum class ProotEventContinuityStatus {
    LEGACY_UNSEQUENCED,
    FIRST,
    CONTIGUOUS,
    DUPLICATE_OR_OLD,
    GAP,
}

data class ProotEventContinuityDecision(
    val status: ProotEventContinuityStatus,
    val previousEventSeq: Long,
    val observedEventSeq: Long,
) {
    val requiresSnapshot: Boolean
        get() = status == ProotEventContinuityStatus.GAP
}

/** 每个 PRoot 会话独立维护序号，旧 v1 事件继续兼容但不声称具备完整性。 */
internal class ProotEventContinuityTracker {
    private val lastEventSeqBySession = mutableMapOf<String, Long>()

    fun observe(sessionId: String, eventSeq: Long): ProotEventContinuityDecision {
        if (sessionId.isBlank() || eventSeq <= 0L) {
            return ProotEventContinuityDecision(
                status = ProotEventContinuityStatus.LEGACY_UNSEQUENCED,
                previousEventSeq = 0L,
                observedEventSeq = eventSeq,
            )
        }

        val previous = lastEventSeqBySession[sessionId] ?: 0L
        val status = when {
            previous == 0L -> ProotEventContinuityStatus.FIRST
            eventSeq <= previous -> ProotEventContinuityStatus.DUPLICATE_OR_OLD
            eventSeq == previous + 1L -> ProotEventContinuityStatus.CONTIGUOUS
            else -> ProotEventContinuityStatus.GAP
        }
        if (eventSeq > previous) lastEventSeqBySession[sessionId] = eventSeq
        return ProotEventContinuityDecision(status, previous, eventSeq)
    }

    fun reset(sessionId: String, eventSeq: Long) {
        if (sessionId.isNotBlank() && eventSeq >= 0L) {
            lastEventSeqBySession[sessionId] = eventSeq
        }
    }

    fun clear() {
        lastEventSeqBySession.clear()
    }
}
