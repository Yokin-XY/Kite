package com.kite.app.foundation.runtime

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal enum class WarmProotRunnerFailureKind {
    START_FAILED,
    PROTOCOL_FAILED,
    RUNNER_CRASHED,
    RUNNER_REJECTED,
    WATCHDOG_TIMEOUT,
    CLOSED,
}

internal data class WarmProotJobExecution(
    val jobId: String,
    val started: Boolean,
    val runnerPid: Long? = null,
    val rootPid: Long? = null,
    val processGroupId: Long? = null,
    val systemSessionId: Long? = null,
    val exitCode: Int? = null,
    val termSignal: Int? = null,
    val cancelled: Boolean = false,
    val timedOut: Boolean = false,
    val stdoutTail: ByteArray = byteArrayOf(),
    val stderrTail: ByteArray = byteArrayOf(),
    val stdoutDroppedBytes: Long = 0L,
    val stderrDroppedBytes: Long = 0L,
    val failureKind: WarmProotRunnerFailureKind? = null,
    val failureReason: String = "",
) {
    val completed: Boolean get() = failureKind == null && exitCode != null
    val succeeded: Boolean get() = completed && exitCode == 0 && termSignal == 0 && !cancelled && !timedOut

    /** 只有 job 尚未 Started 时才允许调用方回退独立 PRoot，避免副作用被重复执行。 */
    val fallbackAllowed: Boolean get() = !started
}

internal interface WarmProotRunnerProcess {
    val input: InputStream
    val output: OutputStream
    val error: InputStream
    val isAlive: Boolean
    fun waitFor(timeoutMs: Long): Boolean
    fun destroy()
    fun destroyForcibly()
}

internal class JavaWarmProotRunnerProcess(private val process: Process) : WarmProotRunnerProcess {
    override val input: InputStream get() = process.inputStream
    override val output: OutputStream get() = process.outputStream
    override val error: InputStream get() = process.errorStream
    override val isAlive: Boolean get() = process.isAlive
    override fun waitFor(timeoutMs: Long): Boolean = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    override fun destroy() = process.destroy()
    override fun destroyForcibly() { process.destroyForcibly() }
}

/**
 * 一个 Android 持有的温热 PRoot session。调用方必须在 IO 线程调用 [executeBlocking]。
 *
 * 同一 runner 严格串行；取消可从其他线程发送。协议/EOF/watchdog 失败会销毁整个 PRoot，且绝不自动重放已 Started job。
 */
internal class WarmProotRunnerController(
    private val processFactory: () -> WarmProotRunnerProcess,
    private val startupTimeoutMs: Long = 5_000L,
    private val watchdogGraceMs: Long = 2_000L,
    private val cancelGraceMs: Long = 1_500L,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : AutoCloseable {
    private sealed interface RunnerEvent {
        data class Frame(val value: WarmProotRunnerFrame) : RunnerEvent
        data class Failure(val kind: WarmProotRunnerFailureKind, val reason: String) : RunnerEvent
    }

    private data class Session(
        val process: WarmProotRunnerProcess,
        val runnerPid: Long,
        val events: LinkedBlockingQueue<RunnerEvent>,
        val diagnostics: BoundedByteTail,
        val writerLock: Any = Any(),
    )

    private val stateLock = Any()
    private val executionLock = Any()

    @Volatile
    private var session: Session? = null

    @Volatile
    private var closed = false

    @Volatile
    private var activeJobId: String? = null

    fun executeBlocking(
        request: WarmProotJobRequest,
        onOutput: (WarmProotOutputStream, ByteArray) -> Unit = { _, _ -> },
    ): WarmProotJobExecution = synchronized(executionLock) {
        WarmProotRunnerProtocol.validate(request)
        if (closed) return@synchronized failure(request.jobId, WarmProotRunnerFailureKind.CLOSED, "runner_controller_closed")
        val activeSession = ensureSession().getOrElse { error ->
            return@synchronized failure(
                request.jobId,
                WarmProotRunnerFailureKind.START_FAILED,
                error.message ?: "runner_start_failed",
            )
        }
        val stdout = BoundedByteTail(request.maxOutputBytesPerStream)
        val stderr = BoundedByteTail(request.maxOutputBytesPerStream)
        var startedFrame: WarmProotRunnerFrame.Started? = null
        var cancelSentAt: Long? = null
        activeJobId = request.jobId
        try {
            if (!write(activeSession, WarmProotRunnerProtocol.encodeRun(request))) {
                invalidate(activeSession, "runner_request_write_failed")
                return@synchronized failure(
                    request.jobId,
                    WarmProotRunnerFailureKind.RUNNER_CRASHED,
                    "runner_request_write_failed",
                )
            }
            val watchdogDeadline = monotonicMs() + request.timeoutMs + watchdogGraceMs
            while (true) {
                val now = monotonicMs()
                if (now >= watchdogDeadline && cancelSentAt == null) {
                    write(activeSession, WarmProotRunnerProtocol.encodeCancel(request.jobId))
                    cancelSentAt = now
                } else if (cancelSentAt != null && now - cancelSentAt >= cancelGraceMs) {
                    invalidate(activeSession, "runner_watchdog_timeout")
                    return@synchronized execution(
                        request = request,
                        session = activeSession,
                        started = startedFrame,
                        stdout = stdout,
                        stderr = stderr,
                        failureKind = WarmProotRunnerFailureKind.WATCHDOG_TIMEOUT,
                        failureReason = "runner_watchdog_timeout",
                    )
                }

                when (val event = activeSession.events.poll(100L, TimeUnit.MILLISECONDS)) {
                    null -> if (!activeSession.process.isAlive) {
                        invalidate(activeSession, "runner_process_exited")
                        return@synchronized execution(
                            request, activeSession, startedFrame, stdout, stderr,
                            WarmProotRunnerFailureKind.RUNNER_CRASHED, "runner_process_exited"
                        )
                    }
                    is RunnerEvent.Failure -> {
                        invalidate(activeSession, event.reason)
                        return@synchronized execution(
                            request, activeSession, startedFrame, stdout, stderr, event.kind, event.reason
                        )
                    }
                    is RunnerEvent.Frame -> when (val frame = event.value) {
                        is WarmProotRunnerFrame.Started -> {
                            if (frame.jobId != request.jobId || startedFrame != null) {
                                return@synchronized protocolFailure(
                                    request, activeSession, startedFrame, stdout, stderr, "runner_started_job_mismatch"
                                )
                            }
                            startedFrame = frame
                        }
                        is WarmProotRunnerFrame.Output -> {
                            if (frame.jobId != request.jobId || startedFrame == null) {
                                return@synchronized protocolFailure(
                                    request, activeSession, startedFrame, stdout, stderr, "runner_output_job_mismatch"
                                )
                            }
                            if (frame.stream == WarmProotOutputStream.STDOUT) stdout.append(frame.bytes) else stderr.append(frame.bytes)
                            runCatching { onOutput(frame.stream, frame.bytes.copyOf()) }
                        }
                        is WarmProotRunnerFrame.Exited -> {
                            if (frame.jobId != request.jobId || startedFrame == null) {
                                return@synchronized protocolFailure(
                                    request, activeSession, startedFrame, stdout, stderr, "runner_exit_job_mismatch"
                                )
                            }
                            return@synchronized execution(
                                request = request,
                                session = activeSession,
                                started = startedFrame,
                                stdout = stdout,
                                stderr = stderr,
                                exit = frame,
                            )
                        }
                        is WarmProotRunnerFrame.Error -> {
                            if (frame.jobId.isNotEmpty() && frame.jobId != request.jobId) {
                                return@synchronized protocolFailure(
                                    request, activeSession, startedFrame, stdout, stderr, "runner_error_job_mismatch"
                                )
                            }
                            return@synchronized execution(
                                request, activeSession, startedFrame, stdout, stderr,
                                WarmProotRunnerFailureKind.RUNNER_REJECTED,
                                "${frame.code}:${frame.message}",
                            )
                        }
                        is WarmProotRunnerFrame.Ready,
                        is WarmProotRunnerFrame.Pong -> return@synchronized protocolFailure(
                            request, activeSession, startedFrame, stdout, stderr, "runner_unexpected_control_frame"
                        )
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            failure(request.jobId, WarmProotRunnerFailureKind.RUNNER_CRASHED, "runner_loop_ended")
        } finally {
            activeJobId = null
        }
    }

    fun cancel(jobId: String): Boolean {
        if (activeJobId != jobId) return false
        val activeSession = session ?: return false
        return write(activeSession, WarmProotRunnerProtocol.encodeCancel(jobId))
    }

    fun isWarm(): Boolean = session?.process?.isAlive == true && !closed

    override fun close() {
        closed = true
        val activeSession = synchronized(stateLock) { session.also { session = null } } ?: return
        write(activeSession, WarmProotRunnerProtocol.encodeShutdown())
        runCatching { activeSession.process.output.close() }
        if (!activeSession.process.waitFor(500L)) {
            activeSession.process.destroy()
            if (!activeSession.process.waitFor(500L)) activeSession.process.destroyForcibly()
        }
    }

    private fun ensureSession(): Result<Session> {
        var startedProcess: WarmProotRunnerProcess? = null
        return runCatching {
            synchronized(stateLock) {
                session?.takeIf { existing -> existing.process.isAlive }?.let { return@synchronized it }
                check(!closed) { "runner_controller_closed" }
                val process = processFactory().also { startedProcess = it }
            val events = LinkedBlockingQueue<RunnerEvent>()
            val diagnostics = BoundedByteTail(MAX_DIAGNOSTIC_BYTES)
            thread(start = true, isDaemon = true, name = "WarmProotRunnerReader") {
                try {
                    while (true) events.put(RunnerEvent.Frame(WarmProotRunnerProtocol.readFrame(process.input)))
                } catch (_: EOFException) {
                    events.offer(RunnerEvent.Failure(WarmProotRunnerFailureKind.RUNNER_CRASHED, "runner_stdout_eof"))
                } catch (error: Throwable) {
                    events.offer(
                        RunnerEvent.Failure(
                            WarmProotRunnerFailureKind.PROTOCOL_FAILED,
                            error.message ?: "runner_protocol_failed",
                        )
                    )
                }
            }
            thread(start = true, isDaemon = true, name = "WarmProotRunnerDiagnostics") {
                val buffer = ByteArray(2048)
                runCatching {
                    while (true) {
                        val count = process.error.read(buffer)
                        if (count < 0) break
                        if (count > 0) diagnostics.append(buffer.copyOf(count))
                    }
                }
            }
                when (val ready = events.poll(startupTimeoutMs, TimeUnit.MILLISECONDS)) {
                    is RunnerEvent.Frame -> {
                        val frame = ready.value as? WarmProotRunnerFrame.Ready
                            ?: throw WarmProotRunnerProtocolException("runner_ready_missing")
                        Session(process, frame.runnerPid, events, diagnostics).also { created -> session = created }
                    }
                    is RunnerEvent.Failure -> throw IllegalStateException(ready.reason)
                    null -> throw IllegalStateException("runner_start_timeout")
                }
            }
        }.onFailure {
            synchronized(stateLock) {
                session?.process?.destroyForcibly()
                session = null
                startedProcess?.let { process ->
                    runCatching { process.output.close() }
                    process.destroyForcibly()
                }
            }
        }
    }

    private fun write(session: Session, bytes: ByteArray): Boolean = synchronized(session.writerLock) {
        runCatching {
            session.process.output.write(bytes)
            session.process.output.flush()
        }.isSuccess
    }

    private fun protocolFailure(
        request: WarmProotJobRequest,
        session: Session,
        started: WarmProotRunnerFrame.Started?,
        stdout: BoundedByteTail,
        stderr: BoundedByteTail,
        reason: String,
    ): WarmProotJobExecution {
        invalidate(session, reason)
        return execution(
            request, session, started, stdout, stderr, WarmProotRunnerFailureKind.PROTOCOL_FAILED, reason
        )
    }

    private fun invalidate(target: Session, reason: String) {
        synchronized(stateLock) {
            if (session !== target) return
            session = null
            runCatching { target.process.output.close() }
            target.process.destroy()
            if (!target.process.waitFor(250L)) target.process.destroyForcibly()
            target.events.offer(RunnerEvent.Failure(WarmProotRunnerFailureKind.RUNNER_CRASHED, reason))
        }
    }

    private fun execution(
        request: WarmProotJobRequest,
        session: Session,
        started: WarmProotRunnerFrame.Started?,
        stdout: BoundedByteTail,
        stderr: BoundedByteTail,
        failureKind: WarmProotRunnerFailureKind? = null,
        failureReason: String = "",
        exit: WarmProotRunnerFrame.Exited? = null,
    ) = WarmProotJobExecution(
        jobId = request.jobId,
        started = started != null,
        runnerPid = session.runnerPid,
        rootPid = started?.rootPid,
        processGroupId = started?.processGroupId,
        systemSessionId = started?.systemSessionId,
        exitCode = exit?.exitCode,
        termSignal = exit?.termSignal,
        cancelled = exit?.cancelled == true,
        timedOut = exit?.timedOut == true,
        stdoutTail = stdout.bytes(),
        stderrTail = stderr.bytes(),
        stdoutDroppedBytes = stdout.droppedBytes,
        stderrDroppedBytes = stderr.droppedBytes,
        failureKind = failureKind,
        failureReason = failureReason,
    )

    private fun failure(jobId: String, kind: WarmProotRunnerFailureKind, reason: String) =
        WarmProotJobExecution(jobId = jobId, started = false, failureKind = kind, failureReason = reason)

    private class BoundedByteTail(private val maxBytes: Int) {
        private var value = ByteArray(0)
        var droppedBytes: Long = 0L
            private set

        @Synchronized
        fun append(bytes: ByteArray) {
            if (bytes.isEmpty()) return
            val combined = if (value.isEmpty()) bytes else value + bytes
            if (combined.size <= maxBytes) {
                value = combined.copyOf()
            } else {
                val drop = combined.size - maxBytes
                droppedBytes += drop.toLong()
                value = combined.copyOfRange(drop, combined.size)
            }
        }

        @Synchronized
        fun bytes(): ByteArray = value.copyOf()
    }

    private companion object {
        const val MAX_DIAGNOSTIC_BYTES = 16 * 1024
    }
}
