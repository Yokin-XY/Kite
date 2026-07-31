package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerRecord

import android.os.Process as AndroidProcess
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.File
import java.util.concurrent.TimeUnit

data class HostProcessRecord(
    val user: String,
    val pid: Int,
    val parentPid: Int,
    val processGroupId: Int? = null,
    val sessionId: Int? = null,
    val rawState: String,
    val command: String,
    val commandLine: String,
    val rssKb: Long? = null,
    val vmSizeKb: Long? = null,
    val oomScoreAdj: Int? = null,
    val cpuTimeTicks: Long? = null,
    val ioReadBytes: Long? = null,
    val ioWriteBytes: Long? = null,
    val processStartTicks: Long? = null,
)

data class HostProcessIdentityObservation(
    val bootId: String,
    val hostPid: Int,
    val processStartTicks: Long,
) {
    init {
        require(normalizeHostBootId(bootId) == bootId) { "host_process_boot_id_invalid" }
        require(hostPid > 0) { "host_process_pid_invalid" }
        require(processStartTicks > 0L) { "host_process_start_ticks_invalid" }
    }
}

class HostProcessSnapshot internal constructor(
    val allProcesses: List<HostProcessRecord>,
    val appUser: String?,
    val bootId: String? = null,
) {
    val appProcesses: List<HostProcessRecord> =
        if (appUser.isNullOrBlank()) {
            emptyList()
        } else {
            allProcesses.filter { it.user == appUser }
        }

    val appProcessMap: Map<Int, HostProcessRecord> = appProcesses.associateBy { it.pid }
    private val allProcessMap: Map<Int, HostProcessRecord> = allProcesses.associateBy { it.pid }

    fun appProcess(pid: Int): HostProcessRecord? {
        return appProcessMap[pid]
    }

    fun strongIdentity(pid: Int): HostProcessIdentityObservation? {
        val normalizedBootId = normalizeHostBootId(bootId) ?: return null
        val process = appProcess(pid) ?: return null
        val startTicks = process.processStartTicks?.takeIf { it > 0L } ?: return null
        return HostProcessIdentityObservation(
            bootId = normalizedBootId,
            hostPid = process.pid,
            processStartTicks = startTicks,
        )
    }

    fun collectTrackedSubtree(rootPid: Int): List<HostProcessRecord> {
        if (rootPid <= 0 || appProcessMap[rootPid] == null) return emptyList()
        return allProcesses
            .filter { process ->
                process.pid == rootPid || isDescendantOf(process.pid, rootPid)
            }
            .sortedBy { it.pid }
    }

    fun findTrackedRootPid(process: HostProcessRecord, rootPids: Set<Int>): Int? {
        if (rootPids.isEmpty()) return null
        if (process.pid in rootPids) return process.pid

        var currentParent = process.parentPid
        var guard = 0
        while (currentParent > 1 && guard < 64) {
            if (currentParent in rootPids) return currentParent
            currentParent = allProcessMap[currentParent]?.parentPid ?: break
            guard += 1
        }
        return null
    }

    private fun isDescendantOf(pid: Int, rootPid: Int): Boolean {
        var currentParent = allProcessMap[pid]?.parentPid ?: return false
        var guard = 0
        while (currentParent > 1 && guard < 64) {
            if (currentParent == rootPid) return true
            currentParent = allProcessMap[currentParent]?.parentPid ?: break
            guard += 1
        }
        return false
    }

    fun collectContainerSubtree(
        container: ContainerRecord,
        extraCommands: Set<String> = emptySet(),
        includeDefaultCommands: Boolean = true
    ): List<HostProcessRecord> {
        if (appProcesses.isEmpty()) return emptyList()

        val roots = appProcesses
            .filter { process ->
                val cleanCommand = process.command.trimStart('[').trimEnd(']').lowercase()
                cleanCommand == "proot" || process.isContainerLikeProcess(
                    container = container,
                    extraCommands = extraCommands,
                    includeDefaultCommands = includeDefaultCommands
                )
            }
            .map { it.pid }
            .toSet()
        if (roots.isEmpty()) return emptyList()

        return allProcesses.filter { process ->
            process.pid in roots || findTrackedRootPid(process, roots) != null
        }
    }
}

object HostProcessInspector {

    private const val DEFAULT_TIMEOUT_SECONDS = 8L

    fun readSnapshot(
        logTag: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): HostProcessSnapshot {
        val procProcesses = runCatching {
            readProcSnapshot()
        }.onFailure { error ->
            Logger.i(logTag, "读取 Android /proc 进程表失败: ${error.message}")
        }.getOrDefault(emptyList())
        val procAppUser = procProcesses.firstOrNull { it.pid == AndroidProcess.myPid() }?.user
        if (procProcesses.isNotEmpty() && !procAppUser.isNullOrBlank()) {
            val appUser = procAppUser
            return HostProcessSnapshot(
                allProcesses = procProcesses,
                appUser = appUser,
                bootId = readHostBootId(),
            )
        }

        val processes = runCatching {
            executeHostCommand("ps -A", timeoutSeconds)
                .lineSequence()
                .mapNotNull(::parseHostProcessLine)
                .toList()
        }.getOrElse { error ->
            Logger.i(logTag, "读取宿主进程表失败: ${error.message}")
            emptyList()
        }
        val appUser = processes.firstOrNull { it.pid == AndroidProcess.myPid() }?.user
        return HostProcessSnapshot(
            allProcesses = processes,
            appUser = appUser,
            bootId = readHostBootId(),
        )
    }

    private fun readProcSnapshot(): List<HostProcessRecord> {
        val procRoot = File("/proc")
        val processDirs = procRoot.listFiles { file ->
            file.isDirectory && file.name.all { it.isDigit() }
        }.orEmpty()

        return processDirs
            .mapNotNull(::readProcProcess)
            .sortedBy { it.pid }
    }

    private fun readProcProcess(processDir: File): HostProcessRecord? {
        val pid = processDir.name.toIntOrNull() ?: return null
        val status = readStatusMap(File(processDir, "status")) ?: return null
        val procName = status["Name"].orEmpty()
        val parentPid = status["PPid"]?.toIntOrNull() ?: 0
        val rawState = status["State"]?.firstOrNull()?.toString().orEmpty()
        val uid = status["Uid"]
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        val commandLine = readCmdline(File(processDir, "cmdline")).ifBlank { procName }
        val command = normalizeProcCommand(procName, commandLine).ifBlank { return null }
        val procStat = readProcStat(File(processDir, "stat"))
        val ioBytes = readIoBytes(File(processDir, "io"))

        return HostProcessRecord(
            user = uid,
            pid = pid,
            parentPid = parentPid,
            processGroupId = procStat?.processGroupId,
            sessionId = procStat?.sessionId,
            rawState = rawState,
            command = command,
            commandLine = commandLine,
            rssKb = parseStatusKb(status["VmRSS"]),
            vmSizeKb = parseStatusKb(status["VmSize"]),
            oomScoreAdj = readIntFile(File(processDir, "oom_score_adj")),
            cpuTimeTicks = procStat?.cpuTimeTicks,
            ioReadBytes = ioBytes?.first,
            ioWriteBytes = ioBytes?.second,
            processStartTicks = procStat?.processStartTicks,
        )
    }

    private fun readStatusMap(file: File): Map<String, String>? {
        return runCatching {
            file.readLines()
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) {
                        null
                    } else {
                        line.substring(0, separator) to line.substring(separator + 1).trim()
                    }
                }
                .toMap()
        }.getOrNull()
    }

    private fun readCmdline(file: File): String {
        return runCatching {
            file.readBytes()
                .toString(Charsets.UTF_8)
                .replace('\u0000', ' ')
                .trim()
        }.getOrDefault("")
    }

    private fun readIntFile(file: File): Int? {
        return runCatching {
            file.readText().trim().toIntOrNull()
        }.getOrNull()
    }

    private data class ProcStatSummary(
        val processGroupId: Int?,
        val sessionId: Int?,
        val cpuTimeTicks: Long?,
        val processStartTicks: Long?,
    )

    private fun readProcStat(file: File): ProcStatSummary? {
        return runCatching { parseHostProcStat(file.readText()) }
            .getOrNull()
            ?.let { parsed ->
                ProcStatSummary(
                    processGroupId = parsed.processGroupId,
                    sessionId = parsed.sessionId,
                    cpuTimeTicks = parsed.cpuTimeTicks,
                    processStartTicks = parsed.processStartTicks,
                )
            }
    }

    private fun readHostBootId(): String? = runCatching {
        File("/proc/sys/kernel/random/boot_id").readText()
    }.getOrNull()?.let(::normalizeHostBootId)

    private fun readIoBytes(file: File): Pair<Long, Long>? {
        return runCatching {
            val values = file.readLines()
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) {
                        null
                    } else {
                        line.substring(0, separator).trim() to
                            line.substring(separator + 1).trim().toLongOrNull()
                    }
                }
                .filter { it.second != null }
                .associate { it.first to it.second!! }
            (values["read_bytes"] ?: 0L) to (values["write_bytes"] ?: 0L)
        }.getOrNull()
    }

    private fun parseStatusKb(raw: String?): Long? {
        return raw
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.toLongOrNull()
    }

    private fun normalizeProcCommand(procName: String, commandLine: String): String {
        val commandFromArgv = commandLine
            .trim()
            .split(Regex("\\s+"), limit = 2)
            .firstOrNull()
            ?.trim('"', '\'')
            ?.substringAfterLast('/')
            .orEmpty()

        return when {
            procName.isBlank() -> commandFromArgv
            procName.equals("MainThread", ignoreCase = true) && commandFromArgv.isNotBlank() -> commandFromArgv
            else -> procName.trimStart('[').trimEnd(']')
        }
    }

    private fun executeHostCommand(command: String, timeoutSeconds: Long): String {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        return output
    }

    private fun parseHostProcessLine(line: String): HostProcessRecord? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("USER")) return null

        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 9) return null

        return HostProcessRecord(
            user = parts[0],
            pid = parts[1].toIntOrNull() ?: return null,
            parentPid = parts[2].toIntOrNull() ?: return null,
            rawState = parts[7].firstOrNull()?.toString() ?: "",
            command = parts[8].trimStart('[').trimEnd(']'),
            commandLine = parts.drop(8).joinToString(" ")
        )
    }
}

internal data class HostProcStatFields(
    val processGroupId: Int?,
    val sessionId: Int?,
    val cpuTimeTicks: Long?,
    val processStartTicks: Long?,
)

/** `/proc/<pid>/stat` 中 comm 可含空格和右括号，必须从最后一个右括号后解析字段。 */
internal fun parseHostProcStat(raw: String): HostProcStatFields? {
    val endOfCommand = raw.lastIndexOf(')')
    if (endOfCommand < 0 || endOfCommand + 2 >= raw.length) return null
    val fieldsFromState = raw.substring(endOfCommand + 2).trim().split(Regex("\\s+"))
    if (fieldsFromState.size <= 19) return null
    val userTicks = fieldsFromState[11].toLongOrNull() ?: return null
    val systemTicks = fieldsFromState[12].toLongOrNull() ?: return null
    return HostProcStatFields(
        processGroupId = fieldsFromState[2].toIntOrNull(),
        sessionId = fieldsFromState[3].toIntOrNull(),
        cpuTimeTicks = userTicks + systemTicks,
        processStartTicks = fieldsFromState[19].toLongOrNull()?.takeIf { it > 0L },
    )
}

private val HOST_BOOT_ID = Regex(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
)

internal fun normalizeHostBootId(raw: String?): String? = raw
    ?.trim()
    ?.lowercase()
    ?.takeIf { HOST_BOOT_ID.matches(it) }

fun HostProcessRecord.isContainerLikeProcess(
    container: ContainerRecord,
    extraCommands: Set<String> = emptySet(),
    includeDefaultCommands: Boolean = true
): Boolean {
    val normalizedCommand = command.trimStart('[').trimEnd(']').lowercase()
    val normalizedArgs = commandLine.lowercase()
    val pathAliases = WorkSurfaceRuntimeBridge.hostPathAliases(container)

    val allowedCommands = buildSet {
        if (includeDefaultCommands) {
            addAll(DEFAULT_CONTAINER_COMMANDS)
        }
        addAll(extraCommands.map { it.lowercase() })
    }

    return pathAliases.any { alias -> normalizedArgs.contains(alias) } ||
        WorkSurfaceRuntimeBridge.containerPathAliases().any { alias -> normalizedArgs.contains(alias) } ||
        normalizedCommand in allowedCommands
}

private val DEFAULT_CONTAINER_COMMANDS = setOf(
    "proot",
    "sh",
    "bash",
    "python",
    "python3",
    "tmux",
    "node",
    "npm",
    "openclaw",
    "git",
    "apt",
    "apt-get",
    "dpkg",
    "uv",
    "pip",
    "pipx",
    "claude",
    "codex"
)
