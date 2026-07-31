package com.kite.app.foundation.runtime

import android.util.Log
import java.util.LinkedHashMap

internal data class RuntimeLaunchTraceEvent(
    val instanceId: String,
    val stage: String,
    val atElapsedMs: Long,
    val sinceStartMs: Long,
    val sincePreviousMs: Long,
)

internal data class RuntimeLaunchTraceSnapshot(
    val instanceId: String,
    val events: List<RuntimeLaunchTraceEvent>,
    val droppedEventCount: Int,
)

/**
 * 进程内、容量有界的启动分段记录。它只保存阶段名和单调时钟，不保存命令、环境、凭证或输出正文。
 */
internal class RuntimeLaunchTraceStore(
    private val maxRuns: Int = 64,
    private val maxEventsPerRun: Int = 32,
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class MutableRun(
        val instanceId: String,
        val events: MutableList<RuntimeLaunchTraceEvent> = mutableListOf(),
        var droppedEventCount: Int = 0,
    )

    private val runs = LinkedHashMap<String, MutableRun>()
    private val terminalOwners = LinkedHashMap<String, String>()

    @Synchronized
    fun begin(instanceId: String, stage: String): RuntimeLaunchTraceEvent? {
        val cleanInstanceId = normalizeToken(instanceId, MAX_INSTANCE_ID_LENGTH)
        if (cleanInstanceId.isBlank()) return null
        runs.remove(cleanInstanceId)
        terminalOwners.entries.removeAll { it.value == cleanInstanceId }
        return mark(cleanInstanceId, stage)
    }

    @Synchronized
    fun mark(instanceId: String, stage: String, firstOnly: Boolean = false): RuntimeLaunchTraceEvent? {
        val cleanInstanceId = normalizeToken(instanceId, MAX_INSTANCE_ID_LENGTH)
        val cleanStage = normalizeToken(stage, MAX_STAGE_LENGTH)
        if (cleanInstanceId.isBlank() || cleanStage.isBlank()) return null

        val run = runs[cleanInstanceId] ?: MutableRun(cleanInstanceId).also { newRun ->
            while (runs.size >= maxRuns.coerceAtLeast(1)) {
                val evictedInstanceId = runs.entries.firstOrNull()?.key ?: break
                runs.remove(evictedInstanceId)
                terminalOwners.entries.removeAll { it.value == evictedInstanceId }
            }
            runs[cleanInstanceId] = newRun
        }
        if (firstOnly && run.events.any { it.stage == cleanStage }) return null
        if (run.events.size >= maxEventsPerRun.coerceAtLeast(1)) {
            run.droppedEventCount += 1
            return null
        }

        val now = elapsedRealtimeMs().coerceAtLeast(0L)
        val firstAt = run.events.firstOrNull()?.atElapsedMs ?: now
        val previousAt = run.events.lastOrNull()?.atElapsedMs ?: now
        return RuntimeLaunchTraceEvent(
            instanceId = cleanInstanceId,
            stage = cleanStage,
            atElapsedMs = now,
            sinceStartMs = (now - firstAt).coerceAtLeast(0L),
            sincePreviousMs = (now - previousAt).coerceAtLeast(0L),
        ).also(run.events::add)
    }

    @Synchronized
    fun bindTerminal(instanceId: String, terminalSessionId: String): Boolean {
        val cleanInstanceId = normalizeToken(instanceId, MAX_INSTANCE_ID_LENGTH)
        val cleanSessionId = normalizeToken(terminalSessionId, MAX_SESSION_ID_LENGTH)
        if (cleanInstanceId.isBlank() || cleanSessionId.isBlank()) return false
        mark(cleanInstanceId, RuntimeLaunchTrace.TERMINAL_BOUND, firstOnly = true)
        terminalOwners[cleanSessionId] = cleanInstanceId
        return true
    }

    @Synchronized
    fun markTerminal(
        terminalSessionId: String,
        stage: String,
        firstOnly: Boolean = false,
    ): RuntimeLaunchTraceEvent? {
        val cleanSessionId = normalizeToken(terminalSessionId, MAX_SESSION_ID_LENGTH)
        val instanceId = terminalOwners[cleanSessionId] ?: return null
        return mark(instanceId, stage, firstOnly)
    }

    @Synchronized
    fun snapshot(instanceId: String): RuntimeLaunchTraceSnapshot? {
        val cleanInstanceId = normalizeToken(instanceId, MAX_INSTANCE_ID_LENGTH)
        val run = runs[cleanInstanceId] ?: return null
        return RuntimeLaunchTraceSnapshot(
            instanceId = run.instanceId,
            events = run.events.toList(),
            droppedEventCount = run.droppedEventCount,
        )
    }

    @Synchronized
    fun runCount(): Int = runs.size

    @Synchronized
    fun clear() {
        runs.clear()
        terminalOwners.clear()
    }

    private fun normalizeToken(value: String, maxLength: Int): String = value
        .trim()
        .take(maxLength)
        .map { character ->
            if (character.isLetterOrDigit() || character in SAFE_TOKEN_CHARACTERS) character else '_'
        }
        .joinToString("")

    private companion object {
        const val MAX_INSTANCE_ID_LENGTH = 96
        const val MAX_SESSION_ID_LENGTH = 96
        const val MAX_STAGE_LENGTH = 64
        val SAFE_TOKEN_CHARACTERS = setOf('-', '_', '.', ':', '@', '/')
    }
}

/** 跨入口、准备、终端和执行核心的只读诊断桥；不得用于决定运行状态。 */
internal object RuntimeLaunchTrace {
    const val ACTION_RECEIVED = "action_received"
    const val EXISTING_RUN_REUSED = "existing_run_reused"
    const val RESOURCE_PREFLIGHT_STARTED = "resource_preflight_started"
    const val RESOURCE_PREFLIGHT_COMPLETED = "resource_preflight_completed"
    const val RESOURCE_PREFLIGHT_INVALIDATED = "resource_preflight_invalidated"
    const val ACTION_DISPATCHED = "action_dispatched"
    const val EXECUTOR_RECEIVED = "executor_received"
    const val RUNTIME_PREP_STARTED = "runtime_prep_started"
    const val RUNTIME_PREP_READY = "runtime_prep_ready"
    const val RUNTIME_PREP_FAILED = "runtime_prep_failed"
    const val RUNTIME_LEASE_ISSUED = "runtime_lease_issued"
    const val RUNTIME_LEASE_CONSUMED = "runtime_lease_consumed"
    const val TERMINAL_RECORD_CREATED = "terminal_record_created"
    const val TERMINAL_BOUND = "terminal_bound"
    const val TERMINAL_STAGED = "terminal_staged"
    const val TERMINAL_OPEN_REQUESTED = "terminal_open_requested"
    const val TERMINAL_CONFIG_STARTED = "terminal_config_started"
    const val TERMINAL_CONFIG_READY = "terminal_config_ready"
    const val TERMINAL_PROCESS_CREATED = "terminal_process_created"
    const val TERMINAL_FIRST_OUTPUT = "terminal_first_output"
    const val TERMINAL_COMMAND_DISPATCHED = "terminal_command_dispatched"

    private const val LOG_TAG = "RuntimeLaunchTrace"
    private val store = RuntimeLaunchTraceStore()

    @JvmStatic
    fun begin(instanceId: String, stage: String = ACTION_RECEIVED) {
        store.begin(instanceId, stage)?.log()
    }

    @JvmStatic
    fun mark(instanceId: String, stage: String, firstOnly: Boolean = false) {
        store.mark(instanceId, stage, firstOnly)?.log()
    }

    @JvmStatic
    fun bindTerminal(instanceId: String, terminalSessionId: String) {
        if (store.bindTerminal(instanceId, terminalSessionId)) {
            store.snapshot(instanceId)?.events?.lastOrNull()?.log()
        }
    }

    @JvmStatic
    fun markTerminal(terminalSessionId: String, stage: String, firstOnly: Boolean = false) {
        store.markTerminal(terminalSessionId, stage, firstOnly)?.log()
    }

    internal fun snapshot(instanceId: String): RuntimeLaunchTraceSnapshot? = store.snapshot(instanceId)

    internal fun clearForTest() = store.clear()

    private fun RuntimeLaunchTraceEvent.log() {
        // 启动热路径只写 logcat；通用 Logger 会同步落盘，会污染正在测量的启动耗时。
        Log.i(
            "[KFShell]$LOG_TAG",
            "instance=$instanceId stage=$stage sinceStart=${sinceStartMs}ms sincePrevious=${sincePreviousMs}ms",
        )
    }
}
