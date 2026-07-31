package com.kite.app.foundation.service

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.runtime.RuntimeExecutionPayload
import com.kite.app.foundation.runtime.RuntimeExecutionRequest
import com.kite.app.foundation.runtime.RuntimeExecutionRequirement
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SupervisordServiceRole(val label: String) {
    SUPERVISOR_CORE("supervisord 本体"),
    CORE("核心服务"),
    OPTIONAL("可选服务"),
    DISCOVERED("动态发现")
}

enum class SupervisordServiceHealth(val label: String) {
    RUNNING("运行中"),
    STARTING("启动中"),
    STOPPED("已停止"),
    FAILED("异常"),
    UNKNOWN("未知"),
    UNAVAILABLE("不可用")
}

data class SupervisordServiceDefinition(
    val serviceId: String,
    val displayName: String,
    val programName: String,
    val role: SupervisordServiceRole,
    val expectedAutoStart: Boolean,
    val commandHint: String? = null,
    val logPath: String? = null,
    val healthCheckHint: String? = null,
    val failurePolicy: String = "supervisord"
)

data class SupervisordManagedServiceSnapshot(
    val definition: SupervisordServiceDefinition,
    val stateName: String = "UNKNOWN",
    val health: SupervisordServiceHealth = SupervisordServiceHealth.UNKNOWN,
    val pid: Int? = null,
    val uptimeLabel: String? = null,
    val exitCode: Int? = null,
    val failureReason: String? = null,
    val logTail: String? = null,
    val source: String = "unknown"
)

data class SupervisordServiceHealthSnapshot(
    val spaceId: String? = null,
    val supervisorRuntimeId: String? = null,
    val supervisorPid: Int? = null,
    val supervisorRunning: Boolean = false,
    val overallHealth: SupervisordServiceHealth = SupervisordServiceHealth.UNKNOWN,
    val services: List<SupervisordManagedServiceSnapshot> = emptyList(),
    val degraded: Boolean = false,
    val failedServiceCount: Int = 0,
    val collectionSource: String = "unknown",
    val diagnosticSummary: String = "",
    val commandExitCode: Int? = null,
    val refreshedAt: Long = 0L
)

object SupervisordServiceHealthStore {

    private const val LOG_TAG = "SupervisordServiceHealthStore"
    private const val COMMAND_TIMEOUT_SECONDS = 10L
    private const val MIN_REFRESH_INTERVAL_MS = 2_500L
    private const val SUPERVISOR_CONFIG = "/etc/supervisor/supervisord.conf"
    private const val SUPERVISOR_HTTP_SERVER = "http://127.0.0.1:19001"
    private const val LOG_MARKER = "__KF_SUPERVISOR_LOGS__"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(SupervisordServiceHealthSnapshot())
    val snapshot: StateFlow<SupervisordServiceHealthSnapshot> = _snapshot

    @Volatile
    private var refreshJob: Job? = null

    @Volatile
    private var pendingRefresh = false

    @Volatile
    private var lastRefreshAt = 0L

    fun refresh(context: Context, reason: String = "manual") {
        val appContext = context.applicationContext
        synchronized(this) {
            val running = refreshJob
            if (running != null && running.isActive) {
                pendingRefresh = true
                return
            }
            val delayMs = (lastRefreshAt + MIN_REFRESH_INTERVAL_MS - System.currentTimeMillis())
                .coerceAtLeast(0L)
            refreshJob = scope.launch {
                try {
                    if (delayMs > 0L) {
                        kotlinx.coroutines.delay(delayMs)
                    }
                    do {
                        clearPendingRefresh()
                        lastRefreshAt = System.currentTimeMillis()
                        _snapshot.value = collectSnapshot(appContext, reason)
                    } while (consumePendingRefresh())
                } finally {
                    synchronized(this@SupervisordServiceHealthStore) {
                        refreshJob = null
                    }
                }
            }
        }
    }

    private fun collectSnapshot(
        context: Context,
        reason: String
    ): SupervisordServiceHealthSnapshot {
        val space = KFWorkspaceManager.getCurrentSpace(context)
            ?: KFWorkspaceManager.listSpaces(context).firstOrNull()
            ?: KFWorkspaceManager.ensureActiveSpace(context)
        val supervisorId = BackgroundRuntimeRegistry.builtinContainerSupervisorId(space.id)
        val supervisorRecord = BackgroundRuntimeRegistry.get(context, supervisorId)
        val supervisorPid = supervisorRecord?.pid?.takeIf { it > 0 }
        val supervisorRunning = supervisorRecord?.status == BackgroundRuntimeStatus.RUNNING

        if (!supervisorRunning) {
            return buildUnavailableSnapshot(
                spaceId = space.id,
                supervisorRuntimeId = supervisorId,
                supervisorPid = supervisorPid,
                reason = "supervisord runtime is ${supervisorRecord?.status?.name ?: "missing"}"
            )
        }
        val runningSupervisor = supervisorRecord ?: return buildUnavailableSnapshot(
            spaceId = space.id,
            supervisorRuntimeId = supervisorId,
            supervisorPid = supervisorPid,
            reason = "supervisord runtime record missing"
        )

        val result = runSupervisorStatusCommand(context, runningSupervisor)
        if (result.exitCode != 0) {
            Logger.i(
                LOG_TAG,
                "supervisorctl status failed: reason=$reason exit=${result.exitCode} output=${result.output.take(160)}"
            )
            return buildUnavailableSnapshot(
                spaceId = space.id,
                supervisorRuntimeId = supervisorId,
                supervisorPid = supervisorPid,
                reason = "supervisorctl unavailable: ${result.output.lineSequence().firstOrNull().orEmpty().take(160)}",
                commandExitCode = result.exitCode
            )
        }

        val (statusText, logs) = splitStatusAndLogs(result.output)
        val parsedServices = statusText
            .lineSequence()
            .mapNotNull(::parseSupervisorStatusLine)
            .toList()
        val services = if (parsedServices.isEmpty()) {
            listOf(buildSupervisorCoreService(supervisorPid, logs))
        } else {
            parsedServices.map { service ->
                service.copy(logTail = logs[service.definition.programName] ?: service.logTail)
            } + buildSupervisorCoreService(supervisorPid, logs)
        }.distinctBy { it.definition.serviceId }
            .sortedWith(
                compareBy<SupervisordManagedServiceSnapshot> { it.definition.role.ordinal }
                    .thenBy { it.definition.displayName.lowercase() }
            )
        val failed = services.count { it.health == SupervisordServiceHealth.FAILED }
        val degraded = failed > 0 || services.any {
            it.definition.expectedAutoStart &&
                it.health != SupervisordServiceHealth.RUNNING &&
                it.health != SupervisordServiceHealth.STARTING
        }
        val overall = when {
            degraded -> SupervisordServiceHealth.FAILED
            services.any { it.health == SupervisordServiceHealth.UNKNOWN } -> SupervisordServiceHealth.UNKNOWN
            else -> SupervisordServiceHealth.RUNNING
        }
        return SupervisordServiceHealthSnapshot(
            spaceId = space.id,
            supervisorRuntimeId = supervisorId,
            supervisorPid = supervisorPid,
            supervisorRunning = true,
            overallHealth = overall,
            services = services,
            degraded = degraded,
            failedServiceCount = failed,
            collectionSource = "supervisorctl",
            diagnosticSummary = buildDiagnosticSummary(overall, services, "supervisorctl"),
            commandExitCode = result.exitCode,
            refreshedAt = System.currentTimeMillis()
        )
    }

    private fun buildUnavailableSnapshot(
        spaceId: String?,
        supervisorRuntimeId: String?,
        supervisorPid: Int?,
        reason: String,
        commandExitCode: Int? = null
    ): SupervisordServiceHealthSnapshot {
        val core = SupervisordManagedServiceSnapshot(
            definition = supervisorCoreDefinition(),
            stateName = "UNAVAILABLE",
            health = SupervisordServiceHealth.UNAVAILABLE,
            pid = supervisorPid,
            failureReason = reason,
            source = "runtime-record"
        )
        return SupervisordServiceHealthSnapshot(
            spaceId = spaceId,
            supervisorRuntimeId = supervisorRuntimeId,
            supervisorPid = supervisorPid,
            supervisorRunning = false,
            overallHealth = SupervisordServiceHealth.UNAVAILABLE,
            services = listOf(core),
            degraded = true,
            failedServiceCount = 1,
            collectionSource = "fallback",
            diagnosticSummary = reason,
            commandExitCode = commandExitCode,
            refreshedAt = System.currentTimeMillis()
        )
    }

    private fun runSupervisorStatusCommand(
        context: Context,
        record: BackgroundRuntimeRecord
    ): CommandResult {
        val payload = """
            if ! command -v supervisorctl >/dev/null 2>&1; then
              echo "supervisorctl missing"
              exit 127
            fi
            # Make dropped-in /etc/supervisor/conf.d/*.conf visible without requiring manual reread/add.
            # `update` performs reread + add/remove changed groups; status remains the parseable output.
            supervisorctl -c $SUPERVISOR_CONFIG -s "$SUPERVISOR_HTTP_SERVER" update >/dev/null 2>&1 || true
            supervisorctl -c $SUPERVISOR_CONFIG -s "$SUPERVISOR_HTTP_SERVER" status 2>&1
            status_exit=${'$'}?
            echo "$LOG_MARKER"
            for f in /var/log/supervisor/*.log; do
              [ -f "${'$'}f" ] || continue
              echo "__KF_LOG_FILE__:${'$'}f"
              tail -n 8 "${'$'}f" 2>/dev/null
            done
            exit ${'$'}status_exit
        """.trimIndent()
        return executeContainerCommand(
            context = context,
            workingDirectory = record.workingDirectory,
            payload = payload
        )
    }

    private fun executeContainerCommand(
        context: Context,
        workingDirectory: String,
        payload: String
    ): CommandResult {
        return runCatching {
            val config = WorkSurfaceRuntimeBridge.buildRequiredProotExecConfig(
                context = context,
                request = RuntimeExecutionRequest(
                    payload = RuntimeExecutionPayload.CommandLine(payload),
                    workingDirectory = workingDirectory,
                    requirements = setOf(RuntimeExecutionRequirement.FULL_LINUX),
                ),
                selectionReason = "supervisord_health_requires_proot",
                loginShell = false,
            )
            val process = ProcessBuilder(config.command)
                .redirectErrorStream(true)
                .apply { environment().putAll(config.env) }
                .start()
            val output = StringBuilder()
            val reader = thread(start = true, isDaemon = true, name = "SupervisorStatusReader") {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { output.append(it).append('\n') }
                    }
                }
            }
            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            reader.join(1200L)
            if (!finished) {
                process.destroyForcibly()
                CommandResult(-1, output.toString())
            } else {
                CommandResult(process.exitValue(), output.toString())
            }
        }.getOrElse { error ->
            CommandResult(-1, error.message ?: "supervisor status command failed")
        }
    }

    private fun parseSupervisorStatusLine(line: String): SupervisordManagedServiceSnapshot? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("__KF_")) return null
        val parts = trimmed.split(Regex("\\s+"), limit = 3)
        if (parts.size < 2) return null
        val name = parts[0].trim()
        val state = parts[1].trim().uppercase()
        val detail = parts.getOrNull(2).orEmpty()
        val definition = dynamicDefinition(name)
        return SupervisordManagedServiceSnapshot(
            definition = definition,
            stateName = state,
            health = mapSupervisorState(state),
            pid = Regex("""pid\s+(\d+)""").find(detail)?.groupValues?.getOrNull(1)?.toIntOrNull(),
            uptimeLabel = detail.substringAfter("uptime", "").trim().takeIf {
                detail.contains("uptime")
            },
            exitCode = Regex("""exit status\s+(-?\d+)""").find(detail)?.groupValues?.getOrNull(1)?.toIntOrNull(),
            failureReason = detail.takeIf {
                state !in setOf("RUNNING", "STARTING")
            },
            source = "supervisorctl"
        )
    }

    private fun mapSupervisorState(state: String): SupervisordServiceHealth {
        return when (state.uppercase()) {
            "RUNNING" -> SupervisordServiceHealth.RUNNING
            "STARTING", "BACKOFF" -> SupervisordServiceHealth.STARTING
            "STOPPED" -> SupervisordServiceHealth.STOPPED
            "EXITED", "FATAL", "UNKNOWN" -> SupervisordServiceHealth.FAILED
            else -> SupervisordServiceHealth.UNKNOWN
        }
    }

    private fun splitStatusAndLogs(output: String): Pair<String, Map<String, String>> {
        val parts = output.split(LOG_MARKER, limit = 2)
        if (parts.size < 2) return output to emptyMap()
        val logs = linkedMapOf<String, StringBuilder>()
        var currentName: String? = null
        parts[1].lineSequence().forEach { line ->
            if (line.startsWith("__KF_LOG_FILE__:")) {
                val path = line.removePrefix("__KF_LOG_FILE__:").trim()
                currentName = File(path).nameWithoutExtension
                logs.getOrPut(currentName.orEmpty()) { StringBuilder() }
            } else {
                currentName?.let { logs.getOrPut(it) { StringBuilder() }.append(line).append('\n') }
            }
        }
        return parts[0] to logs.mapValues { it.value.toString().trim().takeIf(String::isNotBlank).orEmpty() }
    }

    private fun buildSupervisorCoreService(
        supervisorPid: Int?,
        logs: Map<String, String>
    ): SupervisordManagedServiceSnapshot {
        return SupervisordManagedServiceSnapshot(
            definition = supervisorCoreDefinition(),
            stateName = if (supervisorPid != null) "RUNNING" else "UNKNOWN",
            health = if (supervisorPid != null) SupervisordServiceHealth.RUNNING else SupervisordServiceHealth.UNKNOWN,
            pid = supervisorPid,
            logTail = logs["supervisord"],
            source = "runtime-record"
        )
    }

    private fun supervisorCoreDefinition(): SupervisordServiceDefinition {
        return SupervisordServiceDefinition(
            serviceId = "supervisord",
            displayName = "supervisord",
            programName = "supervisord",
            role = SupervisordServiceRole.SUPERVISOR_CORE,
            expectedAutoStart = true,
            commandHint = "supervisord -n -c $SUPERVISOR_CONFIG",
            logPath = "/var/log/supervisor/supervisord.log",
            healthCheckHint = "supervisorctl -s $SUPERVISOR_HTTP_SERVER status",
            failurePolicy = "managed by BackgroundRuntimeHost restart policy"
        )
    }

    private fun dynamicDefinition(programName: String): SupervisordServiceDefinition {
        val normalized = programName.lowercase()
        val role = when {
            normalized.contains("agent") ||
                normalized.contains("gateway") ||
                normalized.contains("cron") -> SupervisordServiceRole.CORE
            else -> SupervisordServiceRole.DISCOVERED
        }
        return SupervisordServiceDefinition(
            serviceId = programName.replace(Regex("[^A-Za-z0-9_.:-]"), "_"),
            displayName = programName,
            programName = programName,
            role = role,
            expectedAutoStart = role == SupervisordServiceRole.CORE,
            logPath = "/var/log/supervisor/$programName.log",
            healthCheckHint = "supervisorctl -s $SUPERVISOR_HTTP_SERVER status $programName"
        )
    }

    private fun buildDiagnosticSummary(
        overall: SupervisordServiceHealth,
        services: List<SupervisordManagedServiceSnapshot>,
        source: String
    ): String {
        val failed = services.filter { it.health == SupervisordServiceHealth.FAILED }
        if (failed.isNotEmpty()) {
            return "degraded via $source: " + failed.joinToString { service ->
                "${service.definition.displayName}=${service.stateName}"
            }
        }
        return "supervisord services ${overall.name.lowercase()} via $source, count=${services.size}"
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

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}
