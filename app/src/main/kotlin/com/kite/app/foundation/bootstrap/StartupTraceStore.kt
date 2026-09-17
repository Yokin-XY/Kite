package com.kite.app.foundation.bootstrap

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

object StartupTraceStore {
    const val ACTION_STARTUP_READY = "com.kite.app.action.STARTUP_READY"
    private const val PREFS_NAME = "kite_startup_trace"
    private const val STATUS_RUNNING = "running"
    private const val STATUS_READY = "ready"
    private const val STATUS_INTERRUPTED = "interrupted"
    private const val LEGACY_INCOMPLETE_FAILURE_STATUS = "previous_process_incomplete"
    private const val MAX_TIMELINE_CHARS = 4_000
    private const val MAX_STACK_CHARS = 12_000

    data class Failure(
        val status: String,
        val attemptId: String,
        val stage: String,
        val occurredAtMs: Long,
        val exceptionClass: String,
        val exceptionMessage: String,
        val stackTrace: String,
        val timeline: String,
        val deviceSummary: String
    )

    data class SetupFailure(
        val checkId: String,
        val reason: String,
        val occurredAtMs: Long,
    )

    fun prepareProcess(context: Context) {
        val appContext = context.applicationContext
        discardLegacyIncompleteFailure(appContext)
        capturePreviousIncompleteAttempt(appContext)
        beginAttempt(appContext, "application.process_created")
        installUncaughtHandler(appContext)
    }

    fun runApplicationStage(context: Context, stage: String, block: () -> Unit): Boolean {
        markStage(context, "$stage.started")
        return try {
            block()
            markStage(context, "$stage.completed")
            true
        } catch (error: Throwable) {
            recordFailure(context, "stage_failed", stage, error)
            false
        }
    }

    fun markStage(context: Context, stage: String) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val previousTimeline = prefs.getString("current_timeline", "").orEmpty()
        val nextTimeline = appendTimeline(previousTimeline, now, stage)
        prefs.edit()
            .putString("current_status", STATUS_RUNNING)
            .putString("current_stage", stage)
            .putLong("current_stage_at_ms", now)
            .putString("current_timeline", nextTimeline)
            .apply()
    }

    fun markReady(context: Context) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val stage = "main.first_frame_ready"
        val timeline = appendTimeline(
            prefs.getString("current_timeline", "").orEmpty(),
            now,
            stage
        )
        prefs.edit()
            .putString("current_status", STATUS_READY)
            .putString("current_stage", stage)
            .putLong("current_stage_at_ms", now)
            .putString("current_timeline", timeline)
            .commit()
        runCatching {
            context.applicationContext.sendBroadcast(
                Intent(ACTION_STARTUP_READY).setPackage(context.packageName)
            )
        }
    }

    fun hasFailure(context: Context): Boolean =
        prefs(context).getBoolean("failure_pending", false)

    fun readFailure(context: Context): Failure? {
        val prefs = prefs(context)
        if (!prefs.getBoolean("failure_pending", false)) return null
        return Failure(
            status = prefs.getString("failure_status", "unknown").orEmpty(),
            attemptId = prefs.getString("failure_attempt_id", "").orEmpty(),
            stage = prefs.getString("failure_stage", "unknown").orEmpty(),
            occurredAtMs = prefs.getLong("failure_at_ms", 0L),
            exceptionClass = prefs.getString("failure_exception_class", "").orEmpty(),
            exceptionMessage = prefs.getString("failure_exception_message", "").orEmpty(),
            stackTrace = prefs.getString("failure_stack", "").orEmpty(),
            timeline = prefs.getString("failure_timeline", "").orEmpty(),
            deviceSummary = prefs.getString("failure_device", deviceSummary()).orEmpty()
        )
    }

    fun clearFailureForRetry(context: Context) {
        clearPendingFailure(context)
        beginAttempt(context, "guard.retry_requested")
    }

    private fun clearPendingFailure(context: Context) {
        prefs(context).edit()
            .remove("failure_pending")
            .remove("failure_status")
            .remove("failure_attempt_id")
            .remove("failure_stage")
            .remove("failure_at_ms")
            .remove("failure_exception_class")
            .remove("failure_exception_message")
            .remove("failure_stack")
            .remove("failure_timeline")
            .remove("failure_device")
            .commit()
    }

    fun recordGuardFailure(context: Context, stage: String, error: Throwable) {
        recordFailure(context, "guard_failed", stage, error)
    }

    /** 保存首次空间准备中某个真实检查项的失败，后续重启或修复不会抹掉首次失败线索。 */
    fun recordSetupFailure(context: Context, checkId: String, reason: String) {
        val safeId = checkId.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (safeId.isBlank()) return
        val prefix = "setup_failure_$safeId"
        prefs(context).edit()
            .putString("${prefix}_reason", sanitize(reason, MAX_STACK_CHARS))
            .putLong("${prefix}_at_ms", System.currentTimeMillis())
            .commit()
    }

    fun readSetupFailure(context: Context, checkId: String): SetupFailure? {
        val safeId = checkId.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (safeId.isBlank()) return null
        val prefix = "setup_failure_$safeId"
        val prefs = prefs(context)
        if (!prefs.contains("${prefix}_at_ms")) return null
        return SetupFailure(
            checkId = checkId,
            reason = prefs.getString("${prefix}_reason", "").orEmpty(),
            occurredAtMs = prefs.getLong("${prefix}_at_ms", 0L),
        )
    }

    fun reportText(context: Context): String {
        val failure = readFailure(context) ?: return "Kite 没有待处理的启动失败记录。"
        return buildString {
            appendLine("Kite 启动诊断")
            appendLine("状态: ${failure.status}")
            appendLine("最后阶段: ${failure.stage}")
            appendLine("时间: ${failure.occurredAtMs}")
            appendLine("设备: ${failure.deviceSummary}")
            if (failure.exceptionClass.isNotBlank()) {
                appendLine("异常: ${failure.exceptionClass}")
            }
            if (failure.exceptionMessage.isNotBlank()) {
                appendLine("原因: ${failure.exceptionMessage}")
            }
            appendLine()
            appendLine("阶段流水:")
            appendLine(failure.timeline.ifBlank { "无" })
            if (failure.stackTrace.isNotBlank()) {
                appendLine()
                appendLine("异常堆栈:")
                appendLine(failure.stackTrace)
            }
        }.trim()
    }

    /**
     * 生成文件时使用的完整启动阶段记录。它不会显示在页面中，也不会改变失败待处理状态。
     */
    fun traceReportText(context: Context): String {
        val prefs = prefs(context)
        val failure = readFailure(context)
        return buildString {
            appendLine("当前启动尝试")
            appendLine("状态: ${prefs.getString("current_status", "unknown").orEmpty()}")
            appendLine("尝试 ID: ${prefs.getString("current_attempt_id", "").orEmpty()}")
            appendLine("开始时间: ${prefs.getLong("current_started_at_ms", 0L)}")
            appendLine("最后阶段: ${prefs.getString("current_stage", "unknown").orEmpty()}")
            appendLine("阶段时间: ${prefs.getLong("current_stage_at_ms", 0L)}")
            appendLine("设备: ${deviceSummary()}")
            appendLine("阶段流水:")
            appendLine(prefs.getString("current_timeline", "").orEmpty().ifBlank { "无" })

            val interruptedStage = prefs.getString("last_incomplete_stage", "").orEmpty()
            if (interruptedStage.isNotBlank()) {
                appendLine()
                appendLine("上一次中断的启动尝试")
                appendLine("尝试 ID: ${prefs.getString("last_incomplete_attempt_id", "").orEmpty()}")
                appendLine("最后阶段: $interruptedStage")
                appendLine("记录时间: ${prefs.getLong("last_incomplete_at_ms", 0L)}")
                appendLine("阶段流水:")
                appendLine(prefs.getString("last_incomplete_timeline", "").orEmpty().ifBlank { "无" })
            }

            if (failure != null) {
                appendLine()
                appendLine("待处理启动失败")
                appendLine(reportText(context))
            }
        }.trim()
    }

    private fun beginAttempt(context: Context, firstStage: String) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putString("current_attempt_id", UUID.randomUUID().toString())
            .putString("current_status", STATUS_RUNNING)
            .putString("current_stage", firstStage)
            .putLong("current_started_at_ms", now)
            .putLong("current_stage_at_ms", now)
            .putString("current_timeline", "$now|$firstStage")
            .commit()
    }

    private fun capturePreviousIncompleteAttempt(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean("failure_pending", false)) return
        if (prefs.getString("current_status", "") != STATUS_RUNNING) return
        prefs.edit()
            .putString("last_incomplete_attempt_id", prefs.getString("current_attempt_id", "").orEmpty())
            .putString("last_incomplete_stage", prefs.getString("current_stage", "unknown").orEmpty())
            .putLong("last_incomplete_at_ms", System.currentTimeMillis())
            .putString("last_incomplete_timeline", prefs.getString("current_timeline", "").orEmpty())
            .putString("current_status", STATUS_INTERRUPTED)
            .commit()
    }

    private fun discardLegacyIncompleteFailure(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean("failure_pending", false)) return
        if (prefs.getString("failure_status", "") != LEGACY_INCOMPLETE_FAILURE_STATUS) return
        clearPendingFailure(context)
    }

    private fun installUncaughtHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is StartupCrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            StartupCrashHandler(context.applicationContext)
        )
    }

    private fun recordFailure(
        context: Context,
        status: String,
        stage: String,
        error: Throwable
    ) {
        persistFailure(
            context = context,
            status = status,
            stage = stage,
            exceptionClass = error.javaClass.name,
            exceptionMessage = sanitize(error.message.orEmpty(), 800),
            stackTrace = sanitize(stackTraceOf(error), MAX_STACK_CHARS)
        )
    }

    private fun persistFailure(
        context: Context,
        status: String,
        stage: String,
        exceptionClass: String,
        exceptionMessage: String,
        stackTrace: String
    ) {
        val prefs = prefs(context)
        prefs.edit()
            .putBoolean("failure_pending", true)
            .putString("failure_status", status)
            .putString("failure_attempt_id", prefs.getString("current_attempt_id", "").orEmpty())
            .putString("failure_stage", stage)
            .putLong("failure_at_ms", System.currentTimeMillis())
            .putString("failure_exception_class", sanitize(exceptionClass, 300))
            .putString("failure_exception_message", exceptionMessage)
            .putString("failure_stack", stackTrace)
            .putString("failure_timeline", prefs.getString("current_timeline", "").orEmpty())
            .putString("failure_device", deviceSummary())
            .commit()
    }

    private fun appendTimeline(previous: String, now: Long, stage: String): String {
        val combined = if (previous.isBlank()) "$now|$stage" else "$previous\n$now|$stage"
        return combined.takeLast(MAX_TIMELINE_CHARS)
    }

    private fun stackTraceOf(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun sanitize(value: String, maxChars: Int): String {
        val redacted = value
            .replace(Regex("(?i)(access_token|refresh_token|id_token|authorization|code|state)=([^\\s&]+)"), "\$1=<redacted>")
            .replace('\u0000', ' ')
        return redacted.take(maxChars)
    }

    private fun deviceSummary(): String = buildString {
        append(Build.MANUFACTURER.ifBlank { "unknown" })
        append(' ')
        append(Build.MODEL.ifBlank { "unknown" })
        append(" / Android API ")
        append(Build.VERSION.SDK_INT)
        append(" / ")
        append(Build.SUPPORTED_ABIS.joinToString(",").ifBlank { "unknown ABI" })
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private class StartupCrashHandler(
        private val context: Context
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                val prefs = prefs(context)
                val stage = prefs.getString("current_stage", "process.uncaught_exception").orEmpty()
                recordFailure(context, "uncaught_exception", stage, error)
            }
            Process.killProcess(Process.myPid())
        }
    }
}
