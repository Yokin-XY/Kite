package com.kite.app.foundation.runtime

import java.io.File
import org.json.JSONObject

enum class ProotActiveRegistryReadStatus {
    LOADED,
    MISSING,
    PARTIAL,
    UNSTABLE,
}

data class ProotActiveTraceeEntry(
    val telemetrySessionId: String,
    val prootStartMs: Long,
    val prootPid: Int,
    val lastEventSeq: Long,
    val lifecycleSeq: Long,
    val traceePid: Int,
    val traceeVpid: Long,
    val startTimeTicks: Long,
    val processGroupId: Int?,
    val sessionId: Int?,
    val parentTraceePid: Int?,
    val parentTraceeVpid: Long?,
    val parentLifecycleSeq: Long?,
    val lastEventType: ProotTelemetryEventType,
    val executable: String,
    val argvHash: String,
    val argvPreview: String,
    val cwd: String,
    val kfRuntimeId: String,
    val kfUnitId: String,
) {
    val lifecycleId: String
        get() = "$telemetrySessionId:$lifecycleSeq"

    fun processRef(): ProotProcessRef = ProotProcessRef(
        telemetrySessionId = telemetrySessionId,
        prootStartMs = prootStartMs,
        prootPid = prootPid,
        lifecycleSeq = lifecycleSeq,
        hostPid = traceePid,
        guestPid = traceeVpid,
        startTimeTicks = startTimeTicks,
    )
}

data class ProotActiveRegistrySession(
    val telemetrySessionId: String,
    val prootStartMs: Long,
    val prootPid: Int,
    val lastEventSeq: Long,
    val entries: List<ProotActiveTraceeEntry>,
)

data class ProotActiveRegistrySnapshot(
    val status: ProotActiveRegistryReadStatus,
    val rootPath: String,
    val sessions: List<ProotActiveRegistrySession> = emptyList(),
    val unstableSessionIds: List<String> = emptyList(),
    val parseErrors: Int = 0,
) {
    val activeTraceeCount: Int
        get() = sessions.sumOf { it.entries.size }

    val complete: Boolean
        get() = status == ProotActiveRegistryReadStatus.LOADED
}

/**
 * 读取 PRoot 维护的活跃 tracee 注册表。只在冷启动、事件缺口或显式校准时调用。
 * `.updating` 与前后两次 meta 校验共同保证不会接受半次原生更新。
 */
internal class ProotActiveRegistryReader(
    private val root: File,
    private val maxSessionReadAttempts: Int = 3,
) {
    fun read(): ProotActiveRegistrySnapshot = readDirectories(
        root.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .sortedBy(File::getName),
    )

    /**
     * 破坏性动作只读取目标 owner 已知的 PRoot 会话，避免一个无关会话正在更新时
     * 把本次停止降级成更宽泛的扫描或进程组信号。
     */
    fun readSessions(sessionIds: Collection<String>): ProotActiveRegistrySnapshot {
        val directories = sessionIds
            .map(String::trim)
            .filter { it.matches(SAFE_SESSION_ID) }
            .distinct()
            .sorted()
            .map { File(root, it) }
            .filter(File::isDirectory)
        return readDirectories(directories)
    }

    private fun readDirectories(directories: List<File>): ProotActiveRegistrySnapshot {
        if (!root.isDirectory) {
            return ProotActiveRegistrySnapshot(
                status = ProotActiveRegistryReadStatus.MISSING,
                rootPath = root.absolutePath,
            )
        }

        val sessions = mutableListOf<ProotActiveRegistrySession>()
        val unstable = mutableListOf<String>()
        var parseErrors = 0
        directories
            .filterNot { it.name.startsWith(".") }
            .forEach { directory ->
                when (val result = readSession(directory)) {
                    is SessionReadResult.Loaded -> sessions += result.session
                    is SessionReadResult.Unstable -> unstable += result.sessionId
                    SessionReadResult.Missing -> Unit
                    SessionReadResult.Malformed -> parseErrors += 1
                }
            }

        val status = when {
            sessions.isNotEmpty() && unstable.isEmpty() && parseErrors == 0 ->
                ProotActiveRegistryReadStatus.LOADED
            sessions.isNotEmpty() -> ProotActiveRegistryReadStatus.PARTIAL
            unstable.isNotEmpty() || parseErrors > 0 -> ProotActiveRegistryReadStatus.UNSTABLE
            else -> ProotActiveRegistryReadStatus.LOADED
        }
        return ProotActiveRegistrySnapshot(
            status = status,
            rootPath = root.absolutePath,
            sessions = sessions,
            unstableSessionIds = unstable.distinct().sorted(),
            parseErrors = parseErrors,
        )
    }

    private companion object {
        val SAFE_SESSION_ID = Regex("[A-Za-z0-9._-]+")
    }

    private fun readSession(directory: File): SessionReadResult {
        repeat(maxSessionReadAttempts.coerceAtLeast(1)) {
            val marker = File(directory, ".updating")
            if (marker.exists()) return@repeat
            val before = readMeta(File(directory, "meta.json"))
                ?: if (!directory.isDirectory) {
                    return SessionReadResult.Missing
                } else {
                    return@repeat
                }
            if (!before.stable) return@repeat

            val entries = mutableListOf<ProotActiveTraceeEntry>()
            var malformed = false
            directory.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".json") && it.name != "meta.json" }
                .sortedBy(File::getName)
                .forEach { file ->
                    val entry = readEntry(file)
                    if (entry == null ||
                        entry.telemetrySessionId != before.telemetrySessionId ||
                        entry.lastEventSeq > before.lastEventSeq
                    ) {
                        malformed = true
                    } else {
                        entries += entry
                    }
                }
            if (malformed || marker.exists()) return@repeat

            val after = readMeta(File(directory, "meta.json")) ?: return@repeat
            if (before != after || !after.stable || marker.exists()) return@repeat
            return SessionReadResult.Loaded(
                ProotActiveRegistrySession(
                    telemetrySessionId = after.telemetrySessionId,
                    prootStartMs = after.prootStartMs,
                    prootPid = after.prootPid,
                    lastEventSeq = after.lastEventSeq,
                    entries = entries.distinctBy(ProotActiveTraceeEntry::lifecycleId),
                ),
            )
        }
        return if (directory.isDirectory) {
            SessionReadResult.Unstable(directory.name)
        } else {
            SessionReadResult.Missing
        }
    }

    private fun readMeta(file: File): RegistryMeta? = runCatching {
        val json = JSONObject(file.readText())
        RegistryMeta(
            telemetrySessionId = json.getString("telemetrySessionId"),
            prootStartMs = json.getLong("prootStartMs"),
            prootPid = json.getInt("prootPid"),
            lastEventSeq = json.getLong("lastEventSeq"),
            stable = json.optBoolean("stable", false),
        )
    }.getOrNull()

    private fun readEntry(file: File): ProotActiveTraceeEntry? = runCatching {
        val json = JSONObject(file.readText())
        ProotActiveTraceeEntry(
            telemetrySessionId = json.getString("telemetrySessionId"),
            prootStartMs = json.getLong("prootStartMs"),
            prootPid = json.getInt("prootPid"),
            lastEventSeq = json.getLong("lastEventSeq"),
            lifecycleSeq = json.getLong("lifecycleSeq"),
            traceePid = json.getInt("traceePid"),
            traceeVpid = json.getLong("traceeVpid"),
            startTimeTicks = json.optLong("startTimeTicks", 0L),
            processGroupId = json.optPositiveInt("processGroupId"),
            sessionId = json.optPositiveInt("sessionId"),
            parentTraceePid = json.optPositiveInt("parentTraceePid"),
            parentTraceeVpid = json.optPositiveLong("parentTraceeVpid"),
            parentLifecycleSeq = json.optPositiveLong("parentLifecycleSeq"),
            lastEventType = ProotTelemetryEventType.entries.firstOrNull {
                it.name == json.optString("lastEventType")
            } ?: ProotTelemetryEventType.Unknown,
            executable = json.optString("executable", ""),
            argvHash = json.optString("argvHash", ""),
            argvPreview = json.optString("argvPreview", ""),
            cwd = json.optString("cwd", ""),
            kfRuntimeId = json.optString("kfRuntimeId", ""),
            kfUnitId = json.optString("kfUnitId", ""),
        )
    }.getOrNull()

    private fun JSONObject.optPositiveInt(key: String): Int? =
        if (isNull(key)) null else optInt(key).takeIf { it > 0 }

    private fun JSONObject.optPositiveLong(key: String): Long? =
        if (isNull(key)) null else optLong(key).takeIf { it > 0L }

    private data class RegistryMeta(
        val telemetrySessionId: String,
        val prootStartMs: Long,
        val prootPid: Int,
        val lastEventSeq: Long,
        val stable: Boolean,
    )

    private sealed interface SessionReadResult {
        data class Loaded(val session: ProotActiveRegistrySession) : SessionReadResult
        data class Unstable(val sessionId: String) : SessionReadResult
        data object Missing : SessionReadResult
        data object Malformed : SessionReadResult
    }
}
