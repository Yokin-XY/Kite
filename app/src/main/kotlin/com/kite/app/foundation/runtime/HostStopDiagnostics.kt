package com.kite.app.foundation.runtime

import kotlinx.coroutines.delay

data class HostStopAuditSeed(
    val rootPid: Int,
    val trackedBefore: List<HostProcessRecord>
)

data class HostStopAuditReport(
    val rootPid: Int,
    val trackedBeforeCount: Int,
    val remainingAfter: List<HostProcessRecord>
) {
    val orphanCount: Int =
        remainingAfter.count { process ->
            process.pid != rootPid && process.parentPid !in remainingAfter.map { it.pid }.toSet()
        }

    val zombieCount: Int =
        remainingAfter.count { process -> process.rawState.equals("Z", ignoreCase = true) }

    val hasResidualProcesses: Boolean = remainingAfter.isNotEmpty()

    fun toCompactSummary(): String {
        val sample =
            if (remainingAfter.isEmpty()) {
                "none"
            } else {
                remainingAfter.take(5).joinToString { process ->
                    "${process.pid}:${process.command}[${process.rawState.ifBlank { "?" }}]<-${process.parentPid}"
                }
            }
        return "root=$rootPid trackedBefore=$trackedBeforeCount remaining=${remainingAfter.size} orphan=$orphanCount zombie=$zombieCount sample=$sample"
    }

    fun toLogBlock(title: String): String {
        return buildString {
            append("== $title ==\n")
            append(
                "rootPid=$rootPid trackedBefore=$trackedBeforeCount remaining=${remainingAfter.size} orphan=$orphanCount zombie=$zombieCount\n"
            )
            if (remainingAfter.isEmpty()) {
                append("remaining: none\n")
            } else {
                remainingAfter.take(8).forEach { process ->
                    append(
                        " - pid=${process.pid} ppid=${process.parentPid} state=${process.rawState.ifBlank { "?" }} command=${process.command} argv=${process.commandLine}\n"
                    )
                }
                if (remainingAfter.size > 8) {
                    append(" - ... +${remainingAfter.size - 8} more\n")
                }
            }
        }
    }
}

object HostStopAuditor {

    private const val DEFAULT_SETTLE_DELAY_MS = 420L

    fun capture(rootPid: Int, logTag: String): HostStopAuditSeed? {
        if (rootPid <= 0) return null
        val snapshot = HostProcessInspector.readSnapshot(logTag = "$logTag-stop-capture")
        val tracked = snapshot.collectTrackedSubtree(rootPid)
        if (tracked.isEmpty()) {
            return snapshot.appProcess(rootPid)
                ?.let { HostStopAuditSeed(rootPid = rootPid, trackedBefore = listOf(it)) }
        }
        return HostStopAuditSeed(rootPid = rootPid, trackedBefore = tracked)
    }

    suspend fun audit(
        seed: HostStopAuditSeed?,
        logTag: String,
        settleDelayMs: Long = DEFAULT_SETTLE_DELAY_MS
    ): HostStopAuditReport? {
        if (seed == null) return null
        delay(settleDelayMs)
        val snapshot = HostProcessInspector.readSnapshot(logTag = "$logTag-stop-audit")
        val trackedPidSet = seed.trackedBefore.map { it.pid }.toSet()
        val remaining = trackedPidSet
            .mapNotNull(snapshot::appProcess)
            .sortedBy { it.pid }
        return HostStopAuditReport(
            rootPid = seed.rootPid,
            trackedBeforeCount = seed.trackedBefore.size,
            remainingAfter = remaining
        )
    }
}
