package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Service
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 固定 1/2/4/8 矩阵；不接收命令、路径、并发度或其他外部参数。 */
class ProotAdmissionBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        context.startService(Intent(context, ProotAdmissionBenchmarkService::class.java))
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.PROOT_ADMISSION_BENCHMARK"
        const val LOG_TAG = "[KFShell]ProotAdmissionBenchmark"

        fun safe(value: String): String = value.take(160).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:") character else '_'
        }.joinToString("")
    }
}

/** 长矩阵由 Debug service 承担，BroadcastReceiver 立即返回，避免把压力测试本身变成广播 ANR。 */
class ProotAdmissionBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                ProotAdmissionBenchmark.run(applicationContext).forEach { report ->
                    Log.i(ProotAdmissionBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    ProotAdmissionBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${ProotAdmissionBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
                    error,
                )
            } finally {
                running.set(false)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        val running = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private object ProotAdmissionBenchmark {
    private const val BENCH_FILE = "/tmp/kf-proot-admission-bench-32m.bin"
    private const val TIMEOUT_MS = 30_000L
    private val LEVELS = listOf(1, 2, 4, 8)

    fun run(context: Context): List<String> {
        val prepared = executeIndependent(
            context,
            listOf(
                "/bin/dd", "if=/dev/zero", "of=$BENCH_FILE", "bs=1048576", "count=32", "status=none"
            )
        )
        check(prepared.succeeded) { "benchmark_file_prepare_failed_${prepared.exitCode}" }
        val reference = executeIndependent(context, listOf("/usr/bin/sha256sum", BENCH_FILE))
        check(reference.succeeded && reference.stdout.isNotBlank()) { "benchmark_reference_failed" }
        val expected = reference.stdout.substringBefore(' ').trim()

        return LEVELS.map { concurrency -> benchmarkLevel(context, concurrency, expected) }
    }

    private fun benchmarkLevel(context: Context, concurrency: Int, expected: String): String {
        val lanes = RuntimeWorkloadPolicy.defaultLanes().map { lane ->
            if (lane.lane == RuntimeLaneKind.SERVICE) {
                lane.copy(maxConcurrency = 8, backgroundMaxConcurrency = 8, serial = false)
            } else {
                lane
            }
        }
        val admission = ProotJobAdmissionController(
            ProotJobAdmissionPolicy(
                profileGroup = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                lanes = lanes,
                pressure = RuntimePressureLevel.NORMAL,
                globalMaxOverride = concurrency,
            )
        )
        val identity = runnerIdentity(context)
        val pool = WarmProotRunnerPool(
            admission = admission,
            identityProvider = { identity },
            sessionFactory = { BenchmarkSession(context) },
            tuningProvider = {
                WarmProotRunnerPoolTuning(maxWarmRunners = concurrency, idleTimeoutMs = 60_000L)
            },
        )
        try {
            val prewarm = prewarmPool(context, pool, identity, concurrency)
            check(prewarm.all(WarmProotPoolExecution::succeeded)) { "prewarm_failed_$concurrency" }
            check(pool.sessionCount() == concurrency) { "prewarm_session_count_${pool.sessionCount()}_$concurrency" }

            val independent = measureBatch(concurrency) { _ ->
                executeIndependent(context, listOf("/usr/bin/sha256sum", BENCH_FILE)).let { execution ->
                    BatchItem(
                        elapsedMs = execution.elapsedMs,
                        succeeded = execution.succeeded && execution.stdout.startsWith(expected),
                    )
                }
            }
            val warm = measureBatch(concurrency) { index ->
                val id = "matrix-warm-$concurrency-$index"
                val started = SystemClock.elapsedRealtime()
                val execution = pool.executeBlocking(
                    admissionRequest = admission(id),
                    jobRequest = WarmProotJobRequest(
                        jobId = id,
                        argv = listOf("/usr/bin/sha256sum", BENCH_FILE),
                        timeoutMs = TIMEOUT_MS,
                        maxOutputBytesPerStream = 4_096,
                    ),
                )
                BatchItem(
                    elapsedMs = SystemClock.elapsedRealtime() - started,
                    succeeded = execution.succeeded &&
                        execution.execution?.stdoutTail?.toString(Charsets.UTF_8)?.startsWith(expected) == true,
                )
            }
            check(independent.items.all(BatchItem::succeeded)) { "independent_failure_$concurrency" }
            check(warm.items.all(BatchItem::succeeded)) { "warm_failure_$concurrency" }

            val independentThroughput = concurrency * 1_000.0 / independent.wallMs.coerceAtLeast(1L)
            val warmThroughput = concurrency * 1_000.0 / warm.wallMs.coerceAtLeast(1L)
            return "status=ok concurrency=$concurrency " +
                "independentWallMs=${independent.wallMs} independentP50Ms=${p50(independent.times)} " +
                "independentP95Ms=${p95(independent.times)} " +
                "independentThroughput=${format(independentThroughput)} independentAvailDropKb=${independent.availableDropKb} " +
                "warmWallMs=${warm.wallMs} warmP50Ms=${p50(warm.times)} warmP95Ms=${p95(warm.times)} " +
                "warmThroughput=${format(warmThroughput)} warmAvailDropKb=${warm.availableDropKb} " +
                "warmSessions=${pool.sessionCount()} maxAdmitted=${admission.snapshot().maxObservedActive} failures=0"
        } finally {
            pool.close()
            admission.close()
        }
    }

    private fun admission(id: String) = ProotJobAdmissionRequest(
        jobId = id,
        lane = RuntimeLaneKind.SERVICE,
        access = ProotJobAccess.READ_ONLY,
        waitTimeoutMs = TIMEOUT_MS,
    )

    private fun prewarmPool(
        context: Context,
        pool: WarmProotRunnerPool,
        identity: WarmProotRunnerIdentity,
        concurrency: Int,
    ): List<WarmProotPoolExecution> {
        val releaseHostFiles = (0 until concurrency).map { index ->
            File(
                identity.runtime.workspacePath,
                ".kf/system/state/proot-admission-benchmark-release-$concurrency-$index"
            ).also { file ->
                file.parentFile?.mkdirs()
                file.delete()
            }
        }
        val releaseContainerPaths = releaseHostFiles.map { file ->
            "/workspace/.kf/system/state/${file.name}"
        }
        val fifo = executeIndependent(
            context,
            listOf("/usr/bin/mkfifo") + releaseContainerPaths,
        )
        check(fifo.succeeded) { "prewarm_fifo_create_failed_$concurrency" }
        val executor = Executors.newFixedThreadPool(concurrency)
        return try {
            val futures = (0 until concurrency).map { index ->
                executor.submit(Callable {
                    val id = "matrix-prewarm-$concurrency-$index"
                    pool.executeBlocking(
                        admissionRequest = admission(id),
                        jobRequest = WarmProotJobRequest(
                            jobId = id,
                            argv = listOf("/bin/cat", releaseContainerPaths[index]),
                            timeoutMs = 20_000L,
                            maxOutputBytesPerStream = 1_024,
                        ),
                    )
                })
            }
            val deadline = SystemClock.elapsedRealtime() + 20_000L
            while (pool.sessionCount() < concurrency && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(10L)
            }
            check(pool.sessionCount() == concurrency) {
                "prewarm_barrier_session_count_${pool.sessionCount()}_$concurrency"
            }
            releaseHostFiles.forEach { file -> FileOutputStream(file).use { it.write(1) } }
            futures.map { it.get(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        } finally {
            releaseHostFiles.forEach(File::delete)
            executor.shutdownNow()
        }
    }

    private fun runnerIdentity(context: Context): WarmProotRunnerIdentity {
        val basis = checkNotNull(
            WorkSurfaceRuntimeBridge.managedCommandVerificationBasis(context, listOf("kf-runner"))
        ) { "runner_identity_unavailable" }
        return WarmProotRunnerIdentity(
            runtime = basis.runtimeIdentity,
            runner = checkNotNull(basis.commandFiles.singleOrNull()) { "runner_stamp_unavailable" },
        )
    }

    private fun measureBatch(count: Int, task: (Int) -> BatchItem): BatchResult {
        val availableBefore = memAvailableKb()
        val running = AtomicBoolean(true)
        var minimumAvailable = availableBefore
        val memoryThread = thread(start = true, isDaemon = true, name = "ProotAdmissionBenchmarkMemory") {
            while (running.get()) {
                minimumAvailable = minOf(minimumAvailable, memAvailableKb())
                Thread.sleep(5L)
            }
        }
        val started = SystemClock.elapsedRealtime()
        val items = try {
            parallel(count, task)
        } finally {
            running.set(false)
            memoryThread.join(1_000L)
        }
        val wall = SystemClock.elapsedRealtime() - started
        return BatchResult(
            items = items,
            wallMs = wall,
            availableDropKb = (availableBefore - minimumAvailable).coerceAtLeast(0L),
        )
    }

    private fun <T> parallel(count: Int, task: (Int) -> T): List<T> {
        val executor = Executors.newFixedThreadPool(count)
        return try {
            executor.invokeAll((0 until count).map { index -> Callable { task(index) } })
                .map { future -> future.get(TIMEOUT_MS + 10_000L, TimeUnit.MILLISECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun executeIndependent(context: Context, argv: List<String>): IndependentExecution {
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(context = context, argv = argv)
        val started = SystemClock.elapsedRealtime()
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(false)
            .apply { environment().putAll(config.env) }
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outReader = thread(start = true, isDaemon = true) { process.inputStream.use { it.copyTo(stdout) } }
        val errReader = thread(start = true, isDaemon = true) { process.errorStream.use { it.copyTo(stderr) } }
        val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outReader.join(1_000L)
        errReader.join(1_000L)
        return IndependentExecution(
            elapsedMs = SystemClock.elapsedRealtime() - started,
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout.toString(Charsets.UTF_8.name()),
            stderr = stderr.toString(Charsets.UTF_8.name()),
        )
    }

    private fun memAvailableKb(): Long = runCatching {
        File("/proc/meminfo").useLines { lines ->
            lines.first { it.startsWith("MemAvailable:") }
                .split(Regex("\\s+"))[1]
                .toLong()
        }
    }.getOrDefault(0L)

    private fun p50(values: List<Long>): Long = percentile(values, 0.50)
    private fun p95(values: List<Long>): Long = percentile(values, 0.95)
    private fun percentile(values: List<Long>, ratio: Double): Long {
        val sorted = values.sorted()
        val index = kotlin.math.ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.1f", value)

    private class BenchmarkSession(context: Context) : WarmProotJobSession {
        private val controller = WarmProotRunnerController(
            processFactory = { WorkSurfaceRuntimeBridge.startWarmProotRunnerProcess(context) },
            startupTimeoutMs = 10_000L,
        )

        override fun executeBlocking(
            request: WarmProotJobRequest,
            onOutput: (WarmProotOutputStream, ByteArray) -> Unit,
        ): WarmProotJobExecution = controller.executeBlocking(request, onOutput)

        override fun isWarm(): Boolean = controller.isWarm()
        override fun close() = controller.close()
    }

    private data class IndependentExecution(
        val elapsedMs: Long,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val succeeded: Boolean get() = exitCode == 0 && stderr.isBlank()
    }

    private data class BatchItem(val elapsedMs: Long, val succeeded: Boolean)
    private data class BatchResult(
        val items: List<BatchItem>,
        val wallMs: Long,
        val availableDropKb: Long,
    ) {
        val times: List<Long> get() = items.map(BatchItem::elapsedMs)
    }
}
