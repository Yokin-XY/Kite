package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * 由代码 owner 声明的短任务合同。这里只接收结构化 argv，不接收任意 shell 文本，也不负责业务结果。
 */
internal data class BoundedProotTaskRequest(
    val jobId: String,
    val ownerId: String,
    val argv: List<String>,
    val workingDirectory: String = "/workspace",
    val environment: Map<String, String> = emptyMap(),
    val lane: RuntimeLaneKind,
    val access: ProotJobAccess,
    val pressureEssential: Boolean = false,
    val waitTimeoutMs: Long = 5_000L,
    val timeoutMs: Long = 30_000L,
    val maxOutputBytesPerStream: Int = 256 * 1024,
)

internal data class BoundedProotTaskPlan(
    val admission: ProotJobAdmissionRequest,
    val job: WarmProotJobRequest,
)

/** 有界短任务的唯一 warm/独立执行入口；两条路径共用同一 admission lease。 */
internal object BoundedProotTaskExecutor {
    private const val MAX_BOUNDED_TIMEOUT_MS = 120_000L
    private const val MAX_BOUNDED_OUTPUT_BYTES = 1024 * 1024

    fun plan(request: BoundedProotTaskRequest): BoundedProotTaskPlan {
        require(request.jobId.isNotBlank() && request.jobId.length <= 96) { "bounded_job_id_invalid" }
        require(request.ownerId.isNotBlank() && request.ownerId.length <= 160) { "bounded_owner_id_invalid" }
        require(request.lane != RuntimeLaneKind.INTERACTIVE) { "bounded_interactive_lane_not_allowed" }
        require(request.waitTimeoutMs in 1L..30_000L) { "bounded_wait_timeout_invalid" }
        require(request.timeoutMs in 1L..MAX_BOUNDED_TIMEOUT_MS) { "bounded_runtime_timeout_invalid" }
        require(request.maxOutputBytesPerStream in 1..MAX_BOUNDED_OUTPUT_BYTES) {
            "bounded_output_limit_invalid"
        }
        val job = WarmProotJobRequest(
            jobId = request.jobId,
            argv = request.argv,
            workingDirectory = request.workingDirectory,
            environment = request.environment,
            timeoutMs = request.timeoutMs,
            maxOutputBytesPerStream = request.maxOutputBytesPerStream,
        )
        WarmProotRunnerProtocol.validate(job)
        return BoundedProotTaskPlan(
            admission = ProotJobAdmissionRequest(
                jobId = request.jobId,
                ownerId = request.ownerId,
                lane = request.lane,
                access = request.access,
                cancellationMode = ProotJobCancellationMode.TIMEOUT_AND_OWNER,
                resultMode = ProotJobResultMode.CAPTURED_STDIO,
                pressureEssential = request.pressureEssential,
                waitTimeoutMs = request.waitTimeoutMs,
            ),
            job = job,
        )
    }

    fun executeBlocking(
        context: Context,
        request: BoundedProotTaskRequest,
    ): WarmProotPoolExecution {
        val plan = plan(request)
        val execution = WarmProotExecutionCoordinator.executeBlocking(
            context = context.applicationContext,
            admissionRequest = plan.admission,
            jobRequest = plan.job,
            independentFallback = { executeIndependent(context.applicationContext, plan.job) },
        )
        BoundedProotTaskTelemetry.record(request.lane, execution)
        return execution
    }

    private fun executeIndependent(
        context: Context,
        job: WarmProotJobRequest,
    ): WarmProotJobExecution {
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            workingDirectory = job.workingDirectory,
            argv = job.argv,
        )
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(false)
            .apply { environment().putAll(config.env + job.environment) }
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutDropped = AtomicLong(0L)
        val stderrDropped = AtomicLong(0L)
        val stdoutReader = boundedReader(
            "BoundedProotStdout",
            process.inputStream,
            stdout,
            stdoutDropped,
            job.maxOutputBytesPerStream,
        )
        val stderrReader = boundedReader(
            "BoundedProotStderr",
            process.errorStream,
            stderr,
            stderrDropped,
            job.maxOutputBytesPerStream,
        )
        val finished = process.waitFor(job.timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            if (!process.waitFor(500L, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
        stdoutReader.join(1_000L)
        stderrReader.join(1_000L)
        return WarmProotJobExecution(
            jobId = job.jobId,
            started = true,
            exitCode = if (finished) process.exitValue() else -1,
            termSignal = 0,
            timedOut = !finished,
            stdoutTail = stdout.toByteArray(),
            stderrTail = stderr.toByteArray(),
            stdoutDroppedBytes = stdoutDropped.get(),
            stderrDroppedBytes = stderrDropped.get(),
        )
    }

    private fun boundedReader(
        threadName: String,
        input: java.io.InputStream,
        output: ByteArrayOutputStream,
        dropped: AtomicLong,
        limit: Int,
    ) = thread(start = true, isDaemon = true, name = threadName) {
        input.use { source ->
            val buffer = ByteArray(2_048)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                val remaining = limit - output.size()
                val retained = minOf(count, remaining.coerceAtLeast(0))
                if (retained > 0) output.write(buffer, 0, retained)
                if (count > retained) dropped.addAndGet((count - retained).toLong())
            }
        }
    }
}
